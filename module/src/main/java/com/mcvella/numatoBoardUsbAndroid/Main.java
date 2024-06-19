package com.mcvella.numatoBoardUsbAndroid;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.viam.common.v1.Common;
import com.viam.sdk.android.module.Module;
import android.Manifest;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.content.Context;
import android.content.Intent;
import android.app.PendingIntent;
import androidx.core.app.ActivityCompat;
import android.content.pm.PackageManager;


import com.viam.sdk.core.component.generic.Generic;
import com.viam.sdk.core.resource.Model;
import com.viam.sdk.core.resource.ModelFamily;
import com.viam.sdk.core.resource.Registry;
import com.viam.sdk.core.resource.Resource;
import com.viam.sdk.core.resource.ResourceCreatorRegistration;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.Arrays;
import java.util.logging.Logger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import viam.app.v1.Robot;

public class Main {

  private static UsbManager usb;
  private static Context cxt;
  private static final String ACTION_USB_PERMISSION = "com.android.usb.USB_PERMISSION";
  private static final String[] PERMISSIONS = {
          Manifest.permission.ACCESS_COARSE_LOCATION,
          Manifest.permission.ACCESS_FINE_LOCATION
  };

  public static void main(final String[] args) {

    Registry.registerResourceCreator(
        Generic.SUBTYPE,
        NumatoBoard.MODEL,
        new ResourceCreatorRegistration(NumatoBoard::new, NumatoBoard::validateConfig)
    );
    final Module module = new Module(args);
    usb = (UsbManager) module.getParentContext().getSystemService(Context.USB_SERVICE);
    cxt = module.getParentContext();
    module.start();
  }

  public static class NumatoBoard extends Generic {

    public static final Model MODEL = new Model(new ModelFamily("mcvella", "generic"), "numato-board-usb-android");
    private static final Logger LOGGER = Logger.getLogger(NumatoBoard.class.getName());
    private UsbSerialPort board;
    private Integer defaultPwmFreq;
    private Map pwmFreq;
    private Map pwmDuty;
    private Map pwmState;

    public NumatoBoard(Robot.ComponentConfig config,
        Map<Common.ResourceName, Resource> dependencies) {
      super(config.getName());

      // attempt to close on reconfigure.  note that connect is lazy and occurs when a doCommand() is issued
      if ( board != null && board.isOpen()) {
        try {
          board.close();
          board = null;
        } catch (IOException e) {
          LOGGER.warning("Could not disconnect Numato board device; perhaps was not already connected");
        }
      }

      // should probably allow this to be set via config
      defaultPwmFreq = 20000;
    }

    private boolean openBoard() {
      List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usb);
      if (availableDrivers.isEmpty()) {
        LOGGER.severe("No serial USB devices found");
        return false;
      }
      LOGGER.warning("USB devices found:" + Arrays.toString(availableDrivers.toArray()));

      UsbSerialDriver driver;
      try {
          driver = availableDrivers.get(0);
      } catch (Exception ignored) {
          LOGGER.severe("No correct Numato USB serial devices, could not connect");
          return false;
      }

      // Check and grant permissions
      if (!checkAndRequestPermission(usb, driver.getDevice())) {
        LOGGER.severe("Please try again and grant permission to connect to Numato USB serial device");
        return false;
      }

      // Open USB device
      UsbDeviceConnection connection = usb.openDevice(driver.getDevice());
      if (connection == null) {
          LOGGER.severe("Error opening Numato USB device");
          return false;
      }

      try {
        // Open first available serial port
        board = driver.getPorts().get(0);
        board.open(connection);
        board.setParameters(19200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

        LOGGER.info("Successfully connected to Numato board device");
        return true;
      } catch (IOException e) {
        LOGGER.severe("Failure connecting to detected Numato board device");
        return false;
      }
    }
    public static Set<String> validateConfig(final Robot.ComponentConfig ignored) {
      return new HashSet<>();
    }

    private boolean checkAndRequestPermission(UsbManager manager, UsbDevice usbDevice) {
      // Check if permissions already exists
      if (hasPermissions(cxt.getApplicationContext(), PERMISSIONS)
              && manager.hasPermission(usbDevice))
          return true;
      else {
          // Request USB permission
          PendingIntent pendingIntent = PendingIntent.getBroadcast(cxt.getApplicationContext(),
                  0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
          manager.requestPermission(usbDevice, pendingIntent);
          
          // this implies permissions must be accepted within 5 seconds of being asked
          try {
            Thread.sleep(5000);
          } catch (InterruptedException e) {
            return false;
          }
          return true;
      }
    }
    public boolean hasPermissions(Context context, String... permissions) {
      if (context != null && permissions != null) {
          for (String permission : permissions) {
              if (ActivityCompat.checkSelfPermission(context, permission)
                      != PackageManager.PERMISSION_GRANTED) {
                  return false;
              }
          }
      }
      return true;
    }

    private boolean ensureBoardOpen() {
      Boolean needToConnect = false;

      if (board == null)
      {
        needToConnect = true;
      }
      else {
          try {
            // test if connection is still responding.
            // note that the isOpen() command could not reliably tell us this
            byte[] resp = new byte[64];
            board.write("\r".getBytes(), 100);
            board.read(resp, 100);
          } catch (IOException e) {
            LOGGER.severe("Numato NEED TO CONNECT");

            needToConnect = true;
          }
      }

      if (needToConnect) {
        Boolean open = openBoard();
        if (!open) {
          return false;
        }
      }
      return true;
    }

    private sendBoardCommand(String serialCommand, String type) {
      LOGGER.info("Numato serial command" + serialCommand);

      if (type == "write") {
        try {
          board.write(serialCommand.getBytes(), 500);
          return builder.putFields("serial command written", Value.newBuilder().setStringValue(serialCommand).build()).build();
        } catch (IOException e) {
          return builder.putFields("serial write error", Value.newBuilder().setStringValue(serialCommand).build()).build();
        }
      } else {
        byte[] resp = new byte[64];
        try {
          board.write(serialCommand.getBytes(), 1000);
          board.read(resp, 100);
        } catch (IOException e) {
          return builder.putFields("serial read error", Value.newBuilder().setStringValue(serialCommand).build()).build();
        }
        String response = new String(resp, StandardCharsets.UTF_8);
        String rsplit[] = response.split("\\n\\r?");
        // if the board was not yet used, default to "off"
        String toReturn = rsplit.length > 1 ? rsplit[1] : "off";

        return builder.putFields("status", Value.newBuilder().setStringValue(toReturn).build()).build();
      }
    }

    private static void busySleep(long nanos)
    {
      long elapsed;
      final long startTime = System.nanoTime();
      do {
        elapsed = System.nanoTime() - startTime;
      } while (elapsed < nanos);
    }

    private startPwmLoop(Integer pin){
      if (!pwmState.containsKey(pin)) {
        Thread thread = new Thread(() -> {
          if (!pwmFreq.containsKey(pin)) {
            pwmFreq.put(pin, defaultPwmFreq);
          }
          if (!pwmDuty.containsKey(pin)) {
            pwmDuty.put(pin, 0);
          }
          Integer duty = pwmDuty.get(pin);
          
          while(duty > 0)
          {
            freq = pwmFreq.get(pin);
            freqNs = 1000000000 / freq;

            // turn on pin for percent of freq interval
            sendBoardCommand("gpio set " + pin + "\r", "write");
            busySleep(freqNs * duty);

            // turn off pin for the rest of freq interval
            sendBoardCommand("gpio clear " + pin + "\r", "write");
            busySleep(freqNs * (1 - duty));

            // re-read in case it changed
            duty = pwmDuty.get(pin);
          }
          // done, so remove it
          pwmState.remove(pin);
        });

        thread.start();
        pwmState.put(pin, thread);
      }
    }

    @Override
    public Struct doCommand(Map<String, Value> command) {
      final Struct.Builder builder = Struct.newBuilder();

      if (!ensureBoardOpen()) {
        LOGGER.severe("Unable to open Numato");
        return builder.putFields("Unable to open board", Value.newBuilder().setStringValue("error").build()).build();
      }

      String serialCommand = "";
      String type = "write";
      if (command.containsKey("set_gpio")) {
        String state = command.get("value");
        if (state == "high") {
          serialCommand = "gpio set " + command.get("pin").getNumberValue() + "\r";
        } else {
          serialCommand = "gpio clear " + command.get("pin").getNumberValue() + "\r";
        }
      } else if (command.containsKey("get_gpio")) {
        serialCommand = "gpio read " + command.get("pin").getNumberValue() + "\r";
        type = "read";
      } else if (command.containsKey("get_analog")) {
        serialCommand = "adc read " + command.get("pin").getNumberValue() + "\r";
        type = "read";
      } else if (command.containsKey("set_pwm_freq")) {
        Integer pin = command.get("pin").getNumberValue();
        pwmFreq.put(pin, command.get("value").getNumberValue());
        return startPwmLoop(pin);
      } else if (command.containsKey("get_pwm_freq")) {
        Integer toReturn;
        if (pwmFreq.containsKey(command.get("pin").getNumberValue())) {
          toReturn = pwmFreq.get(command.get("pin").getNumberValue());
        } else {
          toReturn = defaultPwmFreq;
        }
        return builder.putFields("status", Value.newBuilder().setStringValue(toReturn).build()).build();
      } else if (command.containsKey("set_pwm_duty")) {
        Integer pin = command.get("pin").getNumberValue();
        pwmDuty.put(pin, command.get("value").getNumberValue());
        return startPwmLoop(pin);
      } else if (command.containsKey("get_pwm_duty")) {
        Integer toReturn;
        if (pwmDuty.containsKey(command.get("pin").getNumberValue())) {
          toReturn = pwmDuty.get(command.get("pin").getNumberValue());
        } else {
          toReturn = 0;
        }
        return builder.putFields("status", Value.newBuilder().setStringValue(toReturn).build()).build();
      }

      return sendBoardCommand(serialCommand, type);
    }

  }
}


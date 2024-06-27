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

import com.viam.component.board.v1.Board.PowerMode;
import com.viam.sdk.core.component.board.Board;
import com.viam.component.board.v1.Board.PowerMode;
import com.viam.sdk.core.exception.MethodNotImplementedException;
import com.viam.sdk.core.resource.Model;
import com.viam.sdk.core.resource.ModelFamily;
import com.viam.sdk.core.resource.Registry;
import com.viam.sdk.core.resource.Resource;
import com.viam.sdk.core.resource.Subtype;
import com.viam.sdk.core.resource.ResourceCreatorRegistration;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

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
        Board.getSUBTYPE(),
        NumatoBoard.MODEL,
        new ResourceCreatorRegistration(NumatoBoard::new, NumatoBoard::validateConfig)
    );
    final Module module = new Module(args);
    usb = (UsbManager) module.getParentContext().getSystemService(Context.USB_SERVICE);
    cxt = module.getParentContext();
    module.start();
  }

  public static class NumatoBoard extends Board {

    public static final Model MODEL = new Model(new ModelFamily("mcvella", "board"), "numato-board-usb-android");
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

    private String sendBoardCommand(String serialCommand, String type) {
      LOGGER.info("Numato serial command" + serialCommand);
      final Struct.Builder builder = Struct.newBuilder();

      if (type == "write") {
        try {
          board.write(serialCommand.getBytes(), 500);
          return "serial command written";
        } catch (IOException e) {
          return "serial write error";
        }
      } else {
        byte[] resp = new byte[64];
        try {
          board.write(serialCommand.getBytes(), 1000);
          board.read(resp, 100);
        } catch (IOException e) {
          return "serial read error";
        }
        String response = new String(resp, StandardCharsets.UTF_8);
        String rsplit[] = response.split("\\n\\r?");
        // if the board was not yet used, default to "off"
        String toReturn = rsplit.length > 1 ? rsplit[1] : "off";

        return toReturn;
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

    private void startPwmLoop(Integer pin){
      if (!pwmState.containsKey(pin)) {
        Thread thread = new Thread(() -> {
          if (!pwmFreq.containsKey(pin)) {
            pwmFreq.put(pin, defaultPwmFreq);
          }
          if (!pwmDuty.containsKey(pin)) {
            pwmDuty.put(pin, 0);
          }
          Double duty = (Double) pwmDuty.get(pin);
          
          while(duty > 0)
          {
            Integer freq = (int) pwmFreq.get(pin);
            Integer freqNs = Math.round(1000000000 / freq);

            // turn on pin for percent of freq interval
            sendBoardCommand("gpio set " + pin + "\r", "write");
            busySleep(Math.round(freqNs * duty));

            // turn off pin for the rest of freq interval
            sendBoardCommand("gpio clear " + pin + "\r", "write");
            busySleep(Math.round(freqNs * (1 - duty)));

            // re-read in case it changed
            duty = (Double) pwmDuty.get(pin);
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
      throw new MethodNotImplementedException("doCommand");
    }

    @Override
    public void setGpioState(String pin, boolean high, Optional<Struct> extra) {
      String serialCommand;
      if (high) {
        serialCommand = "gpio set " + pin + "\r";
      } else {
        serialCommand = "gpio clear " + pin + "\r";
      }
      sendBoardCommand(serialCommand, "write");
    }

    @Override
    public boolean getGpioState( String pin, Optional<Struct> extra) {
      String serialCommand = "gpio read " + pin + "\r";
      String status = sendBoardCommand(serialCommand, "read");
      return status == "on" ? true : false;
    }

    @Override
    public void setPwm(String pin, double dutyCyclePct, Optional<Struct> extra) {
      pwmDuty.put(Integer.parseInt(pin), dutyCyclePct);
      startPwmLoop(Integer.parseInt(pin));
    }
    
    @Override
    public double getPwm(String pin, Optional<Struct> extra) {
      Double currentDuty = 0.0;
      if (pwmDuty.containsKey(Integer.parseInt(pin))) {
        currentDuty = (Double) pwmDuty.get(Integer.parseInt(pin));
      }
      return currentDuty;
    }

    @Override
    public void setPwmFrequency(String pin, int frequencyHz, Optional<Struct> extra) {
      pwmFreq.put(Integer.parseInt(pin), frequencyHz);
      startPwmLoop(Integer.parseInt(pin));
    }
    
    @Override
    public int getPwmFrequency(String pin, Optional<Struct> extra) {
      Integer currentFreq = defaultPwmFreq;
      if (pwmFreq.containsKey(Integer.parseInt(pin))) {
        currentFreq = (Integer) pwmFreq.get(Integer.parseInt(pin));
      }
      return currentFreq;
    }

    @Override
    public void writeAnalog(String pin, int value, Optional<Struct> extra) {
      throw new MethodNotImplementedException("writeAnalog");
    }
    
    @Override
    public int getAnalogReaderValue(String pin, Optional<Struct> extra) {
      String serialCommand = "adc read " + pin + "\r";
      String status = sendBoardCommand(serialCommand, "read");
      return Integer.parseInt(status);
    }
    
    @Override
    public int getDigitalInterruptValue(String digitalInterrupt, Optional<Struct> extra) {
      throw new MethodNotImplementedException("getDigitalInterruptValue");
    }

    @Override
    public Iterator<com.viam.component.board.v1.Board.StreamTicksResponse> streamTicks(List<String> interrupts,  Optional<Struct> extra) {
      throw new MethodNotImplementedException("streamTicks");
    }
    
    @Override
    public void addCallbacks(List<String> interrupts,  Queue<com.viam.component.board.v1.Board.StreamTicksResponse> tickQueue,  Optional<Struct> extra) {
      throw new MethodNotImplementedException("addCallbacks");
    }

    @Override
    public void setPowerMode(PowerMode powerMode, long duration, Optional<Struct> extra) {
      throw new MethodNotImplementedException("setPowerMode");
    }

  }
}


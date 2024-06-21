# viam-numato-board-usb-android
Viam modular component for Numato USB GPIO boards on Android devices

# viam-numato-board-usb-android

This module implements the [Viam board component API](https://docs.viam.com/components/board/) in an mcvella:board:numato-board-usb-android model.
With this component, you can control Numato USB GPIO devices in your Viam Android projects.

## Requirements

Viam server must be running on an Android device that has [OTG capabilities](https://en.wikipedia.org/wiki/USB_On-The-Go), with a [Numato](https://numato.com/) USB serial GPIO board device connected.

Note that currently, the expectation is that this is the only USB serial device connected; it will assume the first serial device is a Numato GPIO board.

## Build and Run

To use this module, follow these instructions to [add a module from the Viam Registry](https://docs.viam.com/registry/configure/#add-a-modular-resource-from-the-viam-registry) and select the `mcvella:board:numato-board-usb-android` model from the [`mcvella:board:numato-board-usb-android` module](https://app.viam.com/module/mcvella/mcvella:board:numato-board-usb-android).

## Configure

> [!NOTE]  
> Before configuring your numato board component, you must [create a machine](https://docs.viam.com/manage/fleet/machines/#add-a-new-machine).

Navigate to the **Config** tab of your robot’s page in [the Viam app](https://app.viam.com/).
Click on the **Components** subtab and click **Create**.
Select the `board` type, then select the `mcvella:board:numato-board-usb-android` model.
Enter a name for your component and click **Create**.

On the new component panel, copy and paste the following attribute template into your pubsub’s **Attributes** box:

```json
{
}
```

> [!NOTE]  
> For more information, see [Configure a Robot](https://docs.viam.com/manage/configuration/).

### Attributes

The following attributes are available for `mcvella:board:numato-board-usb-android`:

| Name | Type | Inclusion | Description |
| ---- | ---- | --------- | ----------- |
| |  |  |  |

### Example Configurations

A typical configuration might look like:

```json
{
}
```

## API Usage

DoCommand() can be used to set a GPIO digital pin high or low, read its current digital state, read its current analog state, as well as control software PWM.

Example:

``` python
# set GPIO state to high on pin 2
await board.do_command({'command': 'set_gpio', 'pin': 2, 'value': 'high'})
# read analog on pin 3
status = await board.do_command({'command': 'get_analog', 'pin': 3})
# Set PWM frequency on pin 4 to 1600 Hz
await board.do_command({'command': 'set_pwm_freq', 'pin': 4, 'value': 1600})
# Get PWM duty cycle on pin 4
await board.do_command({'command': 'get_pwm_duty', 'pin': 4})
```

Commands:

### set_gpio

*set_gpio* allows you to set the state of a GPIO pin on the Numato board.
An integer value representing the index of a pin (starting with 0 for the first) must be passed as *pin*.
*value* must be 'high' or 'low'

### get_gpio

*get_gpio* allows you to read the state of a GPIO pin on the Numato board.
An integer value representing the index of a pin (starting with 0 for the first) must be passed as *pin*.
The response will be 'high' or 'low'

### get_analog

*get_analog* allows you to read the state of an analog pin on the Numato board.
An integer value representing the index of a pin (starting with 0 for the first) must be passed as *pin*.

### set_pwm_freq

*set_pwm_freq* allows you to set the PWM frequency on a pin (in Hz) on the Numato board.
An integer value representing the index of a pin (starting with 0 for the first) must be passed as *pin*.
*value* must be an integer representing PWM frequency in Hz

### get_pwm_freq

*get_pwm_freq* allows you to read the PWM frequency on a pin (in Hz) on the Numato board.
An integer value representing the index of a pin (starting with 0 for the first) must be passed as *pin*.
The response will be an integer representing PWM frequency in Hz

### set_pwm_duty

*set_pwm_duty* allows you to set the PWM duty cycle on a pin on the Numato board.
An integer value representing the index of a pin (starting with 0 for the first) must be passed as *pin*.
*value* must be an float representing PWM duty cycle as a float between 0 and 1, representing % of time in high state

### get_pwm_duty

*get_pwm_duty* allows you to read the PWM duty cycle on a pin on the Numato board.
An integer value representing the index of a pin (starting with 0 for the first) must be passed as *pin*.
The response will be a float representing PWM duty cycle as a float between 0 and 1, representing % of time in high state

## TODO

Currently, when a Numato device is plugged-in, you must allow device access via the Android screen within 5 seconds of first DoCommand() call.
This could be improved by adding fixed intent for the device in the module.

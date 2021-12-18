# robocontroller
Prototype version. The completed app can be found here: https://github.com/unlaxedneurotic/bitsrobocontrollerv2
An android app to control a ROS robot via MQTT publish messages

Currently a button based interface that publishes a twist message in JSON format which can then be used to control a robot. A work in progress.
After building the app, you can subscribe to server: tcp://broker.hivemq.com:1883 and topic: robobits/test to get the MQTT messages on a client of your choice.

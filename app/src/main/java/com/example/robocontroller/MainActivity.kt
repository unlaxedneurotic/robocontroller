package com.example.robocontroller

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import com.example.robocontroller.databinding.ActivityMainBinding
import com.google.android.material.slider.Slider
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appContext:Context
    private lateinit var myController:Controller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appContext = applicationContext
        myController = Controller(appContext)
//        binding.setSpeedButton.setOnClickListener{ getSpeed() }
        binding.buttonConnect.setOnClickListener { connect() }
        binding.buttonDisconnect.setOnClickListener { disconnect() }
        binding.speedSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                // Responds to when slider's touch event is being started
            }

            override fun onStopTrackingTouch(slider: Slider) {
                val sliderval = slider.value.toDouble()
                myController.setspeed(sliderval)
                myController.changeSpeed()
                setDisplay()
            }
        })
        setDisplay()
    }

    public fun changeDirection(v:View){
        when(v.id){
            binding.buttonStop.id -> myController.changeSpeed(0)
            binding.buttonForw.id -> myController.changeSpeed(1)
            binding.buttonStrafeForwRight.id ->myController.changeSpeed(2)
            binding.buttonRight.id -> myController.changeSpeed(3)
            binding.buttonStrafeBackRight.id-> myController.changeSpeed(4)
            binding.buttonBack.id -> myController.changeSpeed(5)
            binding.buttonStrafeBackLeft.id -> myController.changeSpeed(6)
            binding.buttonLeft.id -> myController.changeSpeed(7)
            binding.buttonStrafeForwLeft.id -> myController.changeSpeed(8)
        }
        myController.sendData()
        setDisplay()
    }

    fun connect(){

        myController.connectToBroker()
        setDisplay()
    }

    fun disconnect(){
        myController.disconnectFromBroker()
        setDisplay()
    }

    fun getSpeed(){
        val newVal:Double? = binding.setSpeedValDisplay.text.toString().toDoubleOrNull()
        if(newVal!=null){
            myController.setspeed(newVal)
            hideKeyboard()
        }else{ setDisplay() }
    }
    fun setDisplay(){
        binding.setSpeedValDisplay.setText("%.2f".format(myController.max_speed))
        val temp: String = myController.displaystring + "\nConnected: ${myController.isConnected()}"
        binding.temp.text = temp
        hideKeyboard()
    }

    fun hideKeyboard(){
        val view: View? = currentFocus
        if(view!=null){
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken,0)
        }

    }
}

class Controller(val context: Context){
    var max_speed:Double = 0.1
    var speed:Double = 0.0
    var displaystring:String = "Stop"
    val twistdata = twistMessage()
    private var mqttClient: MqttAndroidClient? = null
    var current_mode = 0
    init {
        connectToBroker()
    }

    companion object{
        const val TAG = "MQTT CLIENT"
    }

    fun setspeed(newSpeed:Double){
        speed = newSpeed*max_speed
    }

    fun changeSpeed(mode:Int = current_mode){
//        For a tele_op_twist like keyboard, considering stop as 0
//        Forward button as 1 and counting rest in a clockwise fashion
        current_mode = mode
        for(i in 0..2){
            twistdata.linear[i] = 0.0
            twistdata.angular[i] = 0.0
        }
        displaystring = when(mode){
            0-> "Stop"
            1-> "Forward"
            2-> "Right Forward"
            3-> "Right"
            4-> "Right Backwards"
            5-> "Backwards"
            6-> "Left Backwards"
            7-> "Left"
            8-> "Left Forwards"
            else-> "Invalid"
        }
        displaystring+= " Speed: %.2f".format(speed)
    }

    fun isConnected():Boolean{
        if(mqttClient!=null){
            return mqttClient!!.isConnected
        }
        return false
    }

    fun connectToBroker(){

        if(mqttClient!=null){
            if(mqttClient!!.isConnected) return
        }

        val serverURL = "tcp://broker.hivemq.com:1883"
        mqttClient = MqttAndroidClient(context, serverURL, "Robo Client")
        mqttClient!!.setCallback(object : MqttCallback{
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                Log.d(TAG, "MESSAGE RECEIVED")
            }

            override fun connectionLost(cause: Throwable?) {
                Log.d(TAG, "Connection lost ${cause.toString()}")
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {

            }
        })
        val options = MqttConnectOptions()
        options.connectionTimeout = 60
        try{
            mqttClient!!.connect(options, null, object : IMqttActionListener{
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Connection Success")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAG, "Connection Failure")
                }

            })
        } catch(e:MqttException){
            e.printStackTrace()
        }
    }

    fun disconnectFromBroker(){
        if(mqttClient==null){
            return
        }

        try{
            mqttClient!!.close()
            mqttClient = null
            Log.d(TAG, "CONNECTION CLOSED")

        } catch(e:MqttException){
            e.printStackTrace()
        }
    }

    fun generateJSONmessage() : String{
        val msg = """
            {"twist_message": 
             {
              "linear" : { 
                "x":${twistdata.linear[0]},
                "y":${twistdata.linear[1]},
                "z":${twistdata.linear[2]}
               },
              "angular":{
                "x":${twistdata.linear[0]},
                "y":${twistdata.linear[1]},
                "z":${twistdata.linear[2]}
              }
             }
            }""".trimIndent()
        return msg
    }

    fun sendData(){
        if(mqttClient==null) return
        if(!mqttClient!!.isConnected){ return }

        try{
            val msg = MqttMessage()
            val topic:String = "robobits/test"
            msg.payload = generateJSONmessage().toByteArray()
            msg.qos = 1
            msg.isRetained = false
            mqttClient!!.publish(topic, msg, null, object : IMqttActionListener{
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAG, "Send Failed")
                }

                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Send Success")
                }
            })
        } catch (e:MqttException){
            e.printStackTrace()
        }

        return
    }


}

class twistMessage(){
    var linear: DoubleArray = DoubleArray(3)
    var angular: DoubleArray = DoubleArray(3)
}
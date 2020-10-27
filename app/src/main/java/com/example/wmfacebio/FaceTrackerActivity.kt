package com.example.wmfacebio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.android.synthetic.main.activity_facetracker.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random


typealias LumaListener = (luma: Double) -> Unit

class FaceTrackerActivity : AppCompatActivity() {


    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var tvAction: TextView? = null

    //private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var faceTracking: ImageAnalysis? = null

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    //sonreir = 0, cierra ojos = 2 ...etc.
    private val validationAction = arrayOf<String>("Sonrie","Cierra los ojos","Observa sobre el hombro izquierdo","Observa sobre hombro derecho","Mira arriba","Mira Abajo")

    // number of actions to evaluate before id validation.
    private val actionsRequired = 3


    /**
     * Check permissions
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    /**
     * start Camera if everything is set up.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_facetracker)


        tvAction = findViewById<TextView>(R.id.tvAction)


        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }


        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()
    }


    /**
     * saves image URI in sharedpreferences.
     */
    fun setImgUriSharePref(context: Context, imgUri: Uri) {
        val prefs = context.getSharedPreferences("imgUri", 0)
        val editor = prefs.edit()
        editor.putString("imgUri", imgUri.toString())
        Log.w("shared",Uri.parse(imgUri.toString()).toString())
        editor.commit()
        editor.apply()
    }


    /**
     * Starts camera with image capture use case (video capture is not avaible in CameraX yet)
     * faceTracking is another use case created to analyze each frame gotten from the camera.
     * the analizer has the logic for the face tracking.
     */
    private fun startCamera() {


        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            preview = Preview.Builder()
                .build()

            imageCapture = ImageCapture.Builder().build()



            faceTracking = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            faceTracking!!.setAnalyzer(cameraExecutor, ImageProcessor())


            // Select back camera
            val cameraSelector =
                CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()


                preview?.setSurfaceProvider(viewFinder.createSurfaceProvider())
                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, faceTracking, imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }


    /**
     * Takes photo and saves it the the path you get from getOutputDirectory()
     */
    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return



        // Create timestamped output file to hold the image



        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(
                FILENAME_FORMAT, Locale.US
            ).format(System.currentTimeMillis()) + ".jpg"
        )


        var savedUri = Uri.fromFile(photoFile)






        val msg =  photoFile.toURI()
        Toast.makeText(baseContext, msg.toString(), Toast.LENGTH_SHORT).show()
        //Log.w("reconocio",msg.toString())


        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Setup image capture listener which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {



                    MediaScannerConnection.scanFile(applicationContext, arrayOf(photoFile.absolutePath), null
                    ) { path, uri -> savedUri = uri
                        setImgUriSharePref(applicationContext,uri)
                        Log.i("onScanCompleted", uri.path!!) }




                    val msg = "Photo capture succeeded: $savedUri"
                    setImgUriSharePref(applicationContext,savedUri)
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)

                    val uris = arrayOf(savedUri.toString())

                   // scanFile(photoFile.absolutePath)
                    finish()



                }
            })
    }

    /**
     * used to verify the image was saved taken properly.
     */
    private fun scanFile(path: String) {


        MediaScannerConnection.scanFile(
            this, arrayOf(path), null
        ) { path, uri -> Log.i("TAG", "Finished scanning $path") }


        finish()

    }


    /**
     * checks for permissions.
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * gets output directory where the image/video will be saved for reference.
     */
    private fun getOutputDirectory(): File {

        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, "wmFaceBio").apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }


    /**
     * Contains all the logic for face analisys
     * Random action select an action from the validation action array
     * The counter is in charge of counting the actions made by the user for the validation.
     */
    inner class ImageProcessor : ImageAnalysis.Analyzer {

        val realTimeOpts = FaceDetectorOptions.Builder()
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build()


        var randomAction = validationAction[Random.nextInt(1,4)]

        var actionMsg = ""

        var counter = 1



        // boolean to verify if the first action was assigned.
        var firstAction = false

        private fun firstAction() {

            if (firstAction){
                return
            }
            if (!firstAction) {

                runOnUiThread {
                    actionMsg = "1. "+ randomAction
                    tvAction?.text = actionMsg
                }

                firstAction = true



            }

        }

        /**
         * Each time an action is done properly the phone vibrates to give some feedback to the user.
         */
        private fun vibrate() {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
// Vibrate for 500 milliseconds
// Vibrate for 500 milliseconds
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                //deprecated in API 26
                v.vibrate(500)
            }
        }

        private fun assignAction(previousAction:String): String {

            var newAction = validationAction[Random.nextInt(1,4)]

            while (previousAction==newAction){

                newAction = validationAction[Random.nextInt(1,4)]
            }
            return  newAction
        }

        @SuppressLint("UnsafeExperimentalUsageError")
        override fun analyze(imageProxy: ImageProxy) {


            firstAction()


            val mediaImage = imageProxy.image

            if (mediaImage != null) {

                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                // Pass image to an ML Kit Vision API
                // ...

                val detector = FaceDetection.getClient(realTimeOpts)


                val result = detector.process(image)
                    .addOnSuccessListener { faces ->
                        // Task completed successfully
                        // ...
                        for (face in faces) {

                            val EulerY =face.headEulerAngleY
                            val EulerX =face.headEulerAngleX
                            val EulerZ =face.headEulerAngleZ


                            Log.v("data", "X: $EulerX, Y: $EulerY, z: $EulerZ")


                            //if the user does not look up the analiser will keep waiting before being able to change to another action.
                            if (randomAction== validationAction[4]) {
                                if (EulerX<15){


                                    Log.v("data", "Esperando")
                                }
                                if (EulerX>15){

                                    if (counter < actionsRequired) {

                                        vibrate()
                                        counter+=1
                                        // Toast.makeText(applicationContext,"MIRANDO ARRIBA",Toast.LENGTH_SHORT).show()
                                        Toast.makeText(applicationContext,counter.toString(),Toast.LENGTH_SHORT).show()

                                        randomAction = assignAction(randomAction)
                                        runOnUiThread {
                                            actionMsg = counter.toString()+". "+randomAction
                                            tvAction?.text = actionMsg
                                        }
                                    }

                                   // Toast.makeText(applicationContext,randomAction,Toast.LENGTH_SHORT).show()
                                }
                            }


                            //Look down
                            if (randomAction == validationAction[5]) {

                                if (EulerX<-15){

                                    if (counter < actionsRequired) {

                                        vibrate()
                                        counter+=1
                                        // Toast.makeText(applicationContext,"MIRANDO Abajo",Toast.LENGTH_SHORT).show()
                                        Toast.makeText(applicationContext,counter.toString(),Toast.LENGTH_SHORT).show()

                                        randomAction = assignAction(randomAction)
                                        runOnUiThread {
                                            actionMsg = counter.toString()+". "+randomAction
                                            tvAction?.text = actionMsg
                                        }
                                    }
                                    Toast.makeText(applicationContext,"MIRANDO ABAJO",Toast.LENGTH_SHORT).show()
                            }



                            }
                            //look over your left shoulder.
                            if (randomAction==validationAction[2]) {
                                if (EulerY<25){


                                    Log.v("data", "Esperando")
                                }

                                if (EulerY>25){

                                    if (counter < actionsRequired) {

                                        vibrate()
                                        counter+=1
                                        //    Toast.makeText(applicationContext,"MIRANDO A LA IZQUIERDA",Toast.LENGTH_SHORT).show()
                                        Toast.makeText(applicationContext,counter.toString(),Toast.LENGTH_SHORT).show()
                                        randomAction = assignAction(randomAction)
                                        runOnUiThread {
                                            actionMsg = counter.toString()+". "+randomAction
                                            tvAction?.text = actionMsg
                                        }
                                    }

                                 //   Toast.makeText(applicationContext,randomAction,Toast.LENGTH_SHORT).show()


                                }
                            }

                            // Look over right shoulder
                            if (randomAction==validationAction[3]) {

                                if (EulerX>-25){


                                    Log.v("data", "Esperando")
                                }

                                if (EulerY<-25){

                                    if (counter < actionsRequired) {

                                        vibrate()
                                        counter+=1
                                        // Toast.makeText(applicationContext,"MIRANDO A LA DERECHA",Toast.LENGTH_SHORT).show()
                                        Toast.makeText(applicationContext,counter.toString(),Toast.LENGTH_SHORT).show()
                                        randomAction = assignAction(randomAction)
                                        runOnUiThread {
                                            actionMsg = counter.toString()+". "+randomAction
                                            tvAction?.text = actionMsg
                                        }
                                    }

                                   // Toast.makeText(applicationContext,randomAction,Toast.LENGTH_SHORT).show()

                                }
                            }

                            // Smile
                            if (randomAction==validationAction[0]){

                                if (face.smilingProbability != null) {
                                    val smileProb = face.smilingProbability
                                    Log.v("data", smileProb.toString())
                                    if (smileProb != null) {
                                        if (smileProb>=0.70) {

                                            if (counter < actionsRequired) {

                                                vibrate()
                                                //Toast.makeText(applicationContext,"SONRIENDO",Toast.LENGTH_SHORT).show()
                                                counter+=1
                                                // Toast.makeText(applicationContext,"OJOS CERRADOS",Toast.LENGTH_SHORT).show()
                                                Toast.makeText(applicationContext,counter.toString(),Toast.LENGTH_SHORT).show()
                                                randomAction = assignAction(randomAction)
                                                runOnUiThread {
                                                    actionMsg = counter.toString()+". "+randomAction
                                                    tvAction?.text = actionMsg
                                                }
                                            }

                                        }
                                    }
                                }
                            }


                            //close your eyes.
                            if (randomAction==validationAction[1]) {

                                if (face.rightEyeOpenProbability != null && face.leftEyeOpenProbability!= null) {
                                    val rightEyeOpenProb = face.rightEyeOpenProbability
                                    val leftEyeOpenProb = face.leftEyeOpenProbability
                                    Log.v("data", "ojo derecho: $rightEyeOpenProb.toString() ojo izquierdo: $leftEyeOpenProb.toString()" )
                                    if (leftEyeOpenProb != null && rightEyeOpenProb != null) {
                                        if (leftEyeOpenProb < 0.05 && rightEyeOpenProb <0.05) {


                                            if (counter < actionsRequired) {


                                                vibrate()
                                                counter+=1
                                                // Toast.makeText(applicationContext,"OJOS CERRADOS",Toast.LENGTH_SHORT).show()
                                                Toast.makeText(applicationContext,counter.toString(),Toast.LENGTH_SHORT).show()
                                                randomAction = assignAction(randomAction)
                                                runOnUiThread {
                                                    actionMsg = counter.toString()+". "+randomAction
                                                    tvAction?.text = actionMsg
                                                }
                                            }

                                            //Toast.makeText(applicationContext,randomAction,Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }

                            }

                            //Takes picture to validate can be done with a video too
                            if (counter==actionsRequired) {

                                actionMsg = "processing"

                                runOnUiThread {

                                    tvAction?.text = actionMsg
                                }

                                if (EulerY>-4 && EulerY<4) {




                                    takePhoto()

                                }

                            }



                        }
                        Log.v("data", "cerrando")

                        //Let the frame go so another frame can be analyzed.
                        imageProxy.close()
                    }
                    .addOnFailureListener { e ->
                        // Task failed with an exception
                        // ...
                        Log.v("data", "fallo")
                        e.printStackTrace()
                    }

            }

        }
    }


}




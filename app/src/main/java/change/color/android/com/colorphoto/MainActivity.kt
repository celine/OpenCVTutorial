package change.color.android.com.colorphoto

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.FragmentActivity
import android.support.v4.content.ContextCompat
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.OpenCVLoader
import org.opencv.dnn.Dnn
import org.opencv.imgproc.Imgproc
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame
import org.opencv.core.*
import org.opencv.dnn.Net
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class MainActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2 {
    companion object {
        init{
            OpenCVLoader.initDebug();
        }
    }
    private val REQUEST_CAMERA_PERMISSION = 100;
    private val TAG = "OpenCV/Sample/MobileNet"
    private var net: Net? = null
    private var mOpenCvCameraView: CameraBridgeViewBase? = null


    public override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CAMERA_PERMISSION)
        } else {
            mOpenCvCameraView!!.enableView()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when(requestCode){
            REQUEST_CAMERA_PERMISSION->{
                if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    mOpenCvCameraView!!.enableView()
                }
                return;
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Set up camera listener.
        mOpenCvCameraView = findViewById(R.id.CameraView);
        mOpenCvCameraView!!.setVisibility(CameraBridgeViewBase.VISIBLE)
        mOpenCvCameraView!!.setCvCameraViewListener(this);

    }
    override
    fun onCameraViewStarted(width: Int, height: Int) {
        val proto = getPath("MobileNetSSD_deploy.prototxt", this)
        val weights = getPath("MobileNetSSD_deploy.caffemodel", this)
        Log.d(TAG,"path " + proto + " " + weights);
        net = Dnn.readNetFromCaffe(proto, weights)
        Log.i(TAG, "Network loaded successfully")
    }
    override
    fun onCameraFrame(inputFrame: CvCameraViewFrame): Mat {
        val IN_WIDTH = 300
        val IN_HEIGHT = 300
        val WH_RATIO = IN_WIDTH.toFloat() / IN_HEIGHT
        val IN_SCALE_FACTOR = 0.007843
        val MEAN_VAL = 127.5
        val THRESHOLD = 0.2
        // Get a new frame
        val frame = inputFrame.rgba()
        Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGBA2RGB)
        // Forward image through network.
        val blob = Dnn.blobFromImage(frame, IN_SCALE_FACTOR,
                Size(IN_WIDTH.toDouble(), IN_HEIGHT.toDouble()),
                Scalar(MEAN_VAL, MEAN_VAL, MEAN_VAL), false, false)
        net!!.setInput(blob)
        var detections = net!!.forward()
        var cols = frame.cols()
        var rows = frame.rows()
        val cropSize: Size
        if (cols.toFloat() / rows > WH_RATIO) {
            cropSize = Size(rows * WH_RATIO.toDouble(), rows.toDouble())
        } else {
            cropSize = Size(cols.toDouble(), (cols / WH_RATIO).toDouble())
        }
        val y1 = (rows - cropSize.height) / 2
        val y2 = (y1 + cropSize.height)
        val x1 = (cols - cropSize.width) / 2
        val x2 = (x1 + cropSize.width)
        val subFrame = frame.submat(y1.toInt(), y2.toInt(), x1.toInt(), x2.toInt())

        cols = subFrame.cols()
        rows = subFrame.rows()
        detections = detections.reshape(1, detections.total().toInt() / 7)
        for (i in 0 until detections.rows()) {
            val confidence = detections.get(i, 2)[0]
            if (confidence > THRESHOLD) {
                val classId = detections.get(i, 1)[0].toInt()
                val xLeftBottom = (detections.get(i, 3)[0] * cols).toInt()
                val yLeftBottom = (detections.get(i, 4)[0] * rows).toInt()
                val xRightTop = (detections.get(i, 5)[0] * cols).toInt()
                val yRightTop = (detections.get(i, 6)[0] * rows).toInt()
                // Draw rectangle around detected object.
                Imgproc.rectangle(subFrame, Point(xLeftBottom.toDouble(), yLeftBottom.toDouble()),
                        Point(xRightTop.toDouble(), yRightTop.toDouble()),
                        Scalar(0.0, 255.0, 0.0))
                val label = classNames[classId] + ": " + confidence
                val baseLine = IntArray(1)
                val labelSize = Imgproc.getTextSize(label, Core.FONT_HERSHEY_SIMPLEX, 2.0, 2, baseLine)
                // Draw background for label.
                Imgproc.rectangle(subFrame, Point(xLeftBottom.toDouble(), yLeftBottom - labelSize.height),
                        Point(xLeftBottom + labelSize.width, (yLeftBottom + baseLine[0]).toDouble()),
                        Scalar(255.0, 255.0, 255.0), Core.FILLED)
                // Write class name and confidence.
                Imgproc.putText(subFrame, label, Point(xLeftBottom.toDouble(), yLeftBottom.toDouble()),
                        Core.FONT_HERSHEY_COMPLEX, 2.0, Scalar(0.0, 0.0, 0.0))
            }
        }
        return frame
    }
    override
    fun onCameraViewStopped() {}
    // Upload file to storage and return a path.
    private fun getPath(file: String, context: Context): String {
        val assetManager = context.getAssets()
        var inputStream: BufferedInputStream? = null
        try {
            // Read data from assets.
            inputStream = BufferedInputStream(assetManager.open(file))
            val data = ByteArray(inputStream!!.available())
            inputStream!!.read(data)
            inputStream!!.close()
            // Create copy file in storage.
            val outFile = File(context.getFilesDir(), file)
            val os = FileOutputStream(outFile)
            os.write(data)
            os.close()
            // Return a path to file which may be read in common way.
            return outFile.getAbsolutePath()
        } catch (ex: IOException) {
            Log.i(TAG, "Failed to upload a file")
        }

        return ""
    }

    private val classNames = arrayOf("background", "aeroplane", "bicycle", "bird", "boat", "bottle", "bus", "car", "cat", "chair", "cow", "diningtable", "dog", "horse", "motorbike", "person", "pottedplant", "sheep", "sofa", "train", "tvmonitor")
    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */

}

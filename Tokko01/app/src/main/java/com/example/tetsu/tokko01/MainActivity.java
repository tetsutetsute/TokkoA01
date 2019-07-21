package com.example.tetsu.tokko01;


import java.io.*;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.*;
import android.os.*;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.*;
import android.graphics.*;
import android.widget.*;
import android.provider.*;

import com.microsoft.projectoxford.face.*;
import com.microsoft.projectoxford.face.contract.*;

public class MainActivity extends AppCompatActivity {

    private final int PICK_IMAGE = 1;
    private ProgressDialog detectionProgressDialog;


    //private final String apiEndpoint = "<endpoint>";
    //↑の<endpoint>部分をWebAPIのURLに置き換える↓
    private final String apiEndpoint = "https://westcentralus.api.cognitive.microsoft.com/face/v1.0";

    //private final String subscriptionKey = "<subscriptionKey>";
    //↑の<subscriptionKey>をmicrosoftから指定されたkey(暗号みたいなもん)に置き換える↓
//    private final String subscriptionKey = "5ad1ceacf92b4611bfebfb5a76738485";
    private final String subscriptionKey = "1b4e1d4b1ec047229d3ea6bc547216d2";

    private final FaceServiceClient faceServiceClient =
            new FaceServiceRestClient(apiEndpoint, subscriptionKey);

    private SoundPool soundPool;

    private Uri _imageUri;
    private final String _imageFileName = "Tokko01.jpg";

    @Override
    //onCreateは、画面表示時のメソッドみたいな。初期化とかリスナーの登録とか。
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        Button button1 = findViewById(R.id.button1);
//        button1.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
//                intent.setType("image/*");
//                startActivityForResult(Intent.createChooser(
//                        intent, "Select Picture"), PICK_IMAGE);
//            }
//        });

        //プログレス（進捗）表示はいらんかな。
        detectionProgressDialog = new ProgressDialog(this);

        soundPool = new SoundPool(5, AudioManager.STREAM_MUSIC, 0);
    }

    //カメラびゅう（つまり画面）クリック時の動作
    //びゅうはapp/res/layout/activity_main.xmlで初期化、設定してるよ。
    public void onCameraImageClick(View view) {

        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            String[] permissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
            ActivityCompat.requestPermissions(this, permissions, 2000);
            return;
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, _imageFileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

        ContentResolver resolver = getContentResolver();
        _imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        //スマホのキャプチャ(カメラ)機能(暗黙intent)
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        intent.putExtra(MediaStore.EXTRA_OUTPUT, _imageUri);

        //intentをリクエストコード:200で呼び出し。200っていうのを下のonActibityResultでつかう。
        startActivityForResult(intent, 200);
    }

    @Override
    //アクティビティからの応答をキャッチ
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //マイクロソフトのテンプレ部分コメントアウト
//        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK &&
//                data != null && data.getData() != null) {
//            Uri uri = data.getData();
//            try {
//                Bitmap bitmap = MediaStore.Images.Media.getBitmap(
//                        getContentResolver(), uri);
//                ImageView imageView = findViewById(R.id.imageView1);
//                imageView.setImageBitmap(bitmap);
//
//                // Comment out for tutorial
//                detectAndFrame(bitmap);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }

        //リクエストコード：200のとき！
        if (requestCode == 200 && resultCode == RESULT_OK) {
            try {
                //キャプチャしたbitmap(画像)データをメソッドdetectAndFrameに渡す。
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                        getContentResolver(), _imageUri);

                ImageView imageView = findViewById(R.id.ivCamera);
                imageView.setImageBitmap(bitmap);

                detectAndFrame(bitmap);
            }catch (Exception e){
                e.printStackTrace();
            }
        }

    }


    private void detectAndFrame(final Bitmap imageBitmap) {
        //streamは基礎知識なので調べといて。
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        //conpress→圧縮
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(outputStream.toByteArray());

        AsyncTask<InputStream, String, Face[]> detectTask =
                new AsyncTask<InputStream, String, Face[]>() {
                    String exceptionMessage = "";

                    @Override
                    protected Face[] doInBackground(InputStream... params) {
                        try {
                            publishProgress("Detecting...");
                            //faceServiceClient→マイクロソフト様がおつくりになられたWebAPI職人クラス。
                            Face[] result = faceServiceClient.detect(
                                    params[0],
                                    true,         // returnFaceId
                                    false,        // returnFaceLandmarks
                                    //null          // returnFaceAttributes:
                                    //顔の属性で受け取る項目を指定。
                                    new FaceServiceClient.FaceAttributeType[] {
                                            //FaceServiceClient.FaceAttributeType.Age,
                                            //FaceServiceClient.FaceAttributeType.Gender,
                                            FaceServiceClient.FaceAttributeType.Emotion,
                                            FaceServiceClient.FaceAttributeType.Smile
                                    }

                            );
                            //進捗報告はいらなそう。
                            if (result == null){
                                publishProgress(
                                        "Detection Finished. Nothing detected");
                                return null;
                            }
                            publishProgress(String.format(
                                    "Detection Finished. %d face(s) detected",
                                    result.length));

                            return result;
                        } catch (Exception e) {
                            exceptionMessage = String.format(
                                    "Detection failed: %s", e.getMessage());
                            return null;
                        }
                    }

                    @Override
                    protected void onPreExecute() {
                        //TODO: show progress dialog
                        detectionProgressDialog.show();
                    }
                    @Override
                    protected void onProgressUpdate(String... progress) {
                        //TODO: update progress
                        detectionProgressDialog.setMessage(progress[0]);
                    }
                    @Override
                    protected void onPostExecute(Face[] result) {
                        //TODO: update face frames
                        detectionProgressDialog.dismiss();

                        if(!exceptionMessage.equals("")){
                            //うーん。
                            showError(exceptionMessage);
                        }
                        if (result == null) return;

                        for(Face face : result){
                            if(face.faceAttributes.smile < 0.5){
                                // 笑顔じゃないよ。
                            }else{
                                // 笑顔だよ。
                                soundPool.play(R.raw.decision8, 1f, 1f, 0, 0, 1f);
                            }
                        }

                        ImageView imageView = findViewById(R.id.ivCamera);
                        imageView.setImageBitmap(
                                drawFaceRectanglesOnBitmap(imageBitmap, result));
                        imageBitmap.recycle();
                    }
                };

        detectTask.execute(inputStream);
    }

    //エラー表示的なメソッド。いるかなあ。
    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }})
                .create().show();
    }

    //画像の中の顔を四角で囲む。
    private static Bitmap drawFaceRectanglesOnBitmap(
            Bitmap originalBitmap, Face[] faces) {
        Bitmap bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.RED);
        paint.setStrokeWidth(10);
        if (faces != null) {
            for (Face face : faces) {
                FaceRectangle faceRectangle = face.faceRectangle;
                canvas.drawRect(
                        faceRectangle.left,
                        faceRectangle.top,
                        faceRectangle.left + faceRectangle.width,
                        faceRectangle.top + faceRectangle.height,
                        paint);
            }
        }
        return bitmap;
    }

}


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
import android.util.Log;
import android.view.*;
import android.graphics.*;
import android.widget.*;
import android.provider.*;
import android.media.AudioAttributes;

import com.microsoft.projectoxford.face.*;
import com.microsoft.projectoxford.face.contract.*;

public class MainActivity extends AppCompatActivity {

    private final int PICK_IMAGE = 1;
    private ProgressDialog detectionProgressDialog;


    //private final String apiEndpoint = "WebAPIのURL";
    private final String apiEndpoint = "https://westcentralus.api.cognitive.microsoft.com/face/v1.0";

    //private final String subscriptionKey = "microsoftから指定されたsubscriptionKey";
    private final String subscriptionKey = "1b4e1d4b1ec047229d3ea6bc547216d2";

    //faceServiceClient→マイクロソフト様がおつくりになられたWebAPI職人クラス。
    private final FaceServiceClient faceServiceClient =
            new FaceServiceRestClient(apiEndpoint, subscriptionKey);

    //音を出すクラス
    private SoundPool soundPool;
    //音のID
    private int sound_correct,sound_false;

    //撮った画像を保存する先（スマホ内）
    private Uri _imageUri;
    private final String _imageFileName = "Tokko01.jpg";

    @Override
    //onCreateは、画面表示時のメソッドみたいな。初期化とかリスナーの登録とか。
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //↑この2行はほぼテンプレで、最初から書いてある。

        //プログレス（進捗）表示
        detectionProgressDialog = new ProgressDialog(this);

        //効果音初期化
        initSoundPool();
    }

    //カメラびゅう（つまり画面）クリック時の動作
    //びゅうはapp/res/layout/activity_main.xmlで初期化、設定してるよ。
    public void onCameraImageClick(View view) {
        //アプリからスマホへ、ストレージをいじる許可を確認して、ない場合は取得
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

        //スマホのストレージに保存することで画像が小さくならない
        intent.putExtra(MediaStore.EXTRA_OUTPUT, _imageUri);

        //intentをリクエストコード:200で呼び出し。200っていうのを下のonActibityResultでつかう。
        startActivityForResult(intent, 200);
    }

    @Override
    //アクティビティからの応答をキャッチ
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //リクエストコード：200のとき！
        if (requestCode == 200 && resultCode == RESULT_OK) {
            try {
                //キャプチャしたbitmap(画像)データをメソッドdetectAndFrameに渡す。
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                        getContentResolver(), _imageUri);

                ImageView imageView = findViewById(R.id.ivCamera);
                imageView.setImageBitmap(bitmap);

                //これぞ核となる顔認識のメソッドである。
                detectAndFrame(bitmap);

            }catch (Exception e){
                e.printStackTrace();
            }
        }

    }

    //これぞ核となる顔認識のメソッドである。
    private void detectAndFrame(final Bitmap imageBitmap) {
        //streamは基礎知識なので調べといて。
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        //conpress→圧縮
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(outputStream.toByteArray());

        //Async（非同期）Task（処理）。非同期処理なのだ。別スレッドで動くのだ。
        AsyncTask<InputStream, String, Face[]> detectTask =
                new AsyncTask<InputStream, String, Face[]>() {
                    String exceptionMessage = "";

                    @Override
                    protected Face[] doInBackground(InputStream... params) {
                        try {
                            publishProgress("AIが顔を見ています...");

                            Face[] result = faceServiceClient.detect(
                                    params[0],
                                    true,         // returnFaceId
                                    false,        // returnFaceLandmarks
                                     new FaceServiceClient.FaceAttributeType[] {
                                             //受け取る顔の属性を指定
                                            //FaceServiceClient.FaceAttributeType.Age,
                                            //FaceServiceClient.FaceAttributeType.Gender,
                                            FaceServiceClient.FaceAttributeType.Emotion,
                                            FaceServiceClient.FaceAttributeType.Smile
                                    }

                            );
                            //進捗報告
                            if (result == null){
                                publishProgress(
                                        "誰もいない。。");
                                return null;
                            }
                            publishProgress(String.format(
                                    "%d人検知しました。 ",
                                    result.length));


                            return result;
                        } catch (Exception e) {
                            exceptionMessage = String.format(
                                    "検知に失敗しました: %s", e.getMessage());
                            return null;
                        }
                    }

                    @Override
                    protected void onPreExecute() {
                        //プログレスダイアログ表示
                        detectionProgressDialog.show();
                    }
                    @Override
                    protected void onProgressUpdate(String... progress) {
                        //TODO: update progress
                        detectionProgressDialog.setMessage(progress[0]);
                    }
                    @Override
                    protected void onPostExecute(Face[] result) {

                        //プログレスダイアログさようなら
                        detectionProgressDialog.dismiss();

                        if(!exceptionMessage.equals("")){
                            showError(exceptionMessage);
                        }
                        if (result == null) return;

                        for(Face face : result){

                            //笑顔ポイントを取得
                            Double smilePoint = face.faceAttributes.smile;

                            //笑顔ポイントによって判定
                            if(smilePoint < 0.2){
                                playJudgeSound(false);
                                showSmileComment("笑顔ってなんだっけ");
                            }else if(0.2 <= smilePoint){
                                playJudgeSound(false);
                                showSmileComment("笑顔かなぁ……");
                            }
                            else if(0.4 <= smilePoint){
                                playJudgeSound(false);
                                showSmileComment("ちょっと笑顔");
                            }
                            else if(0.6 <= smilePoint){
                                playJudgeSound(true);
                                showSmileComment("うん、笑顔");
                            }
                            else if(0.8 <= smilePoint){
                                playJudgeSound(true);
                                showSmileComment("誰もが認める笑顔");
                            }
                        }

                        //撮った画像を画面に表示
                        ImageView imageView = findViewById(R.id.ivCamera);
                        imageView.setImageBitmap(
                                drawFaceRectanglesOnBitmap(imageBitmap, result));
                        imageBitmap.recycle();
                    }
                };

        //↑で定義した非同期処理を動かすのだ。
        detectTask.execute(inputStream);
    }

    //笑顔判定結果メッセージ表示
    private void showSmileComment(String message){
        new AlertDialog.Builder(this)
                .setTitle("その表情は...")
                .setMessage(message)
                .create().show();
    }

    //エラー表示
    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("エラー")
                .setMessage(message)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }})
                .create().show();
    }

    //判定音初期化(起動や再開時に呼び出しておくこと!)
    private void initSoundPool(){
        soundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 0);
        sound_correct = soundPool.load(this,R.raw.correct3,1);
        sound_false = soundPool.load(this,R.raw.incorrect1,1);
    }

    //判定音発射
    private void playJudgeSound(boolean isCorrect){
        int sound;
        if(isCorrect){
            sound = sound_correct;
        }else{
            sound = sound_false;
        }
        soundPool.play(sound,0.5f,0.5f,0,0,1);
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


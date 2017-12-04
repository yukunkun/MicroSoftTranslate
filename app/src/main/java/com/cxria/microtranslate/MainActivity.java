package com.cxria.microtranslate;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.StringTokenizer;


public class MainActivity extends AppCompatActivity {
    String Tag=this.getClass().getSimpleName();

    private AudioRecord recorder;
    //录音源
    private static int audioSource = MediaRecorder.AudioSource.MIC;
    //录音的采样频率
    private static int audioRate = 16000;
    //录音的声道，单声道
    private static int audioChannel = AudioFormat.CHANNEL_IN_MONO;
    //量化的深度
    private static int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    //缓存的大小
    private static int bufferSize = AudioRecord.getMinBufferSize(audioRate,audioChannel,audioFormat);
    //记录播放状态
    private boolean isRecording = false;
    //数字信号数组
    private byte [] noteArray;
    private File audioFile;
    private DataOutputStream mDos;
    private TextView mTextView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextView = (TextView) findViewById(R.id.tv_content);
        SocketUtils.connectToServer();
        //init AudioRecord
        recorder = new AudioRecord(audioSource,audioRate,audioChannel,audioFormat,bufferSize);

        SocketUtils.setSocketready(new SocketUtils.Socketready() {
            @Override
            public void socketReady(String txt) {
                try {
                    final JSONObject jsonObject=new JSONObject(txt);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mTextView.setText(jsonObject.optString("recognition")+'\n'+jsonObject.optString("translation"));
                        }
                    });
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        File fpath = new File(Environment.getExternalStorageDirectory()
                .getAbsolutePath() + "/slack");
        fpath.mkdirs();// 创建文件夹
        try {
            // 创建临时文件,注意这里的格式为.pcm
            audioFile = File.createTempFile("recording", ".wav", fpath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 开通输出流到指定的文件
        try {
            mDos = new DataOutputStream(
                    new BufferedOutputStream(
                            new FileOutputStream(audioFile,true)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    //将数据写入文件夹,文件的写入没有做优化
    public void writeData(){
        Log.i(Tag,"recording");
        noteArray = new byte[bufferSize];
        while(isRecording == true){
            //数据的读取回调
            int recordSize = recorder.read(noteArray,0,bufferSize);

            if(recordSize>0){
                SocketUtils.sendMsg(noteArray);
//                Log.i("recordSize",recordSize+"  "+noteArray.length);
                //获取到录音的数组
                // 循环将buffer中的音频数据写入到OutputStream中
                for (int i = 0; i < recordSize; i++) {
                    try {
                        mDos.write(noteArray[i]);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    public void start(View view) {
        //开始录制
        startRecord();
    }

    //开始录音
    public void startRecord(){
        isRecording = true;
        SocketUtils.sendMsg(WriteWaveFileHeader());
        byte[] bytes = WriteWaveFileHeader();
        for (int i = 0; i <bytes.length; i++) {
            try {
                mDos.write(bytes[i]);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        recorder.startRecording();
        //记录数据
        recordData();

    }

    //停止录音
    public void stopRecord(){
        isRecording = false;
        if(recorder!=null)
        recorder.stop();
    }

    //开始线程数据
    public void recordData(){

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (isRecording){
                    Log.i(Tag,"startRecord");
                    writeData();
                }
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecord();
    }
    /*
    任何一种文件在头部添加相应的头文件才能够确定的表示这种文件的格式，wave是RIFF文件结构，每一部分为一个chunk，其中有RIFF WAVE chunk，
    FMT Chunk，Fact chunk,Data chunk,其中Fact chunk是可以选择的，
    */
    private byte[] WriteWaveFileHeader() {
        byte[] header = new byte[44];
        long longSampleRate=16000;
        long byteRate=32000;
        header[0] = 'R'; // RIFF
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = 0;//数据大小
        header[5] = 0;
        header[6] = 0;
        header[7] = 0;
        header[8] = 'W';//WAVE
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        //FMT Chunk
        header[12] = 'f'; // 'fmt '
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';//过渡字节
        //数据大小
        header[16] = (byte) (16 & 0xff); // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        //编码方式 10H为PCM编码格式
        header[20] = 1; // format = 1
        header[21] = 0;
        //通道数
        header[22] = 1;
        header[23] = 0;
        //采样率，每个通道的播放速度
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        //音频数据传送速率,采样率*通道数*采样深度/8
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        // 确定系统一次要处理多少个这样字节的数据，确定缓冲区，通道数*采样位数
        header[32] = (byte)(2&0xff);
        header[33] = 0;
        //每个样本的数据位数
        header[34] = (byte) (16 & 0xff);
        header[35] = 0;
        //Data chunk
        header[36] = 'd';//data
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = 0;
        header[41] = 0;
        header[42] = 0;
        header[43] = 0;
        return header;
    }

    //播放
    public void play(View view) {
        stopRecord();
        MediaPlayer mediaPlayer=MediaPlayer.create(this, Uri.parse(audioFile+""));
        mediaPlayer.start();
    }



}

package com.example.sound.devicesound;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.*;

import java.util.ArrayList;
import java.util.List;

import calsualcoding.reedsolomon.EncoderDecoder;
import google.zxing.common.reedsolomon.ReedSolomonException;

public class Listentone {

    int HANDSHAKE_START_HZ = 4096;
    int HANDSHAKE_END_HZ = 5120 + 1024;

    int START_HZ = 1024;
    int STEP_HZ = 256;
    int BITS = 4;

    int FEC_BYTES = 4;

    private int mAudioSource = MediaRecorder.AudioSource.MIC;
    private int mSampleRate = 44100;
    private int mChannelCount = AudioFormat.CHANNEL_IN_MONO;
    private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private float interval = 0.1f;

    private int mBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannelCount, mAudioFormat);

    public AudioRecord mAudioRecord = null;
    int audioEncodig;
    boolean startFlag;
    FastFourierTransformer transform;


    public Listentone(){

        transform = new FastFourierTransformer(DftNormalization.STANDARD);
        startFlag = false;
        mAudioRecord = new AudioRecord(mAudioSource, mSampleRate, mChannelCount, mAudioFormat, mBufferSize);
        mAudioRecord.startRecording();
    }
    private int findPowerSize(int round) {
        int i = 1;
        while (round >= i ) i *= 2;
        return (i - round) > (round - i/2) ? i : i/2;
    }


    public void PreRequest(){
        //decode
        int blocksize = findPowerSize((int)Math.round(interval / 2 * mSampleRate));
        short[] buffer = new short[blocksize];
        boolean fre_flag = false;
        List<Long> list = new ArrayList<>();
        List<Byte> dec = new ArrayList<>();
        EncoderDecoder encoderDecoder = new EncoderDecoder();

        while(true){
            mAudioRecord.read(buffer, 0, blocksize);
            double buffer_D [] = new double[blocksize];

            for(int i = 0; i<blocksize;i++) {
                buffer_D[i] = (double)buffer[i];
            }

            long freq = Math.abs(Math.round(findFrequency(buffer_D)*mSampleRate));
            System.out.println("Frequency_print: " + freq);     //frequency print

            if (match(freq, HANDSHAKE_START_HZ)) fre_flag = true;

            if(fre_flag) {
                long freq_val = Math.abs(new Long((freq - START_HZ) / STEP_HZ));
                freq_val = (((freq - START_HZ) % STEP_HZ) > STEP_HZ / 2) ? freq_val + 1 : freq_val ;
                list.add(freq_val);
                System.out.println("Frequency_val_print: " + Math.abs(new Long((freq - START_HZ) / STEP_HZ)));     //val frequency print
            }
            if (match(freq, HANDSHAKE_END_HZ)&& fre_flag) {
                fre_flag = false;
                dec = decode(list);
                for(Byte index : dec)
                    System.out.println("index of dec: "+index.byteValue());     //print byte
                byte dec_by [] = new byte[dec.size()];
                //change Byte[] to byte[]
                for(int i = 0; i<dec.size();i++){
                    dec_by[i] = dec.get(i).byteValue();
                    System.out.println("dec_by Index: "+dec_by[i]);     //print dec_by
                }
                try{
                    //reed solomon decode
                    dec_by = encoderDecoder.decodeData(dec_by,4);
                }catch (Exception e) {
                    System.out.println("decode reed solomon error");
                    fre_flag = false;
                    list = new ArrayList<Long>();

                    System.out.print("decode here is ascii without reed solomon: ");
                    for (int i = 0; i < dec.size() - 4; i++) {
                         byte temp = dec.get(i).byteValue();
                        System.out.print((char)temp + " ");
                    }

                    System.out.print("\ndecode: here is data without reed solomon: ");
                    for (int i = 0; i < dec.size() - 4; i++) {
                        System.out.print(dec.get(i).byteValue() + " ");
                    }
                    System.out.println();

                    continue;
                }
                //print
                System.out.print("decode data: ");
                for (int i = 0;i<dec_by.length;i++)
                    System.out.print(dec_by[i]+" ");
                System.out.print("\ndecode data ascii code: ");
                for (int i = 0;i<dec_by.length;i++) {
                    System.out.print(String.valueOf((char)dec_by[i])+" ");
                }
                System.out.println();
                // arrayList clear
                list = new ArrayList<Long>();
            }
        }
    }
    //오차범위 수정
    private boolean match(long num1, long num2){
        return Math.abs(num1 - num2) <= 20;
    }

    private List<Byte> decode(List<Long> list){
        //merge 4bits data to 1byte
        List<Byte> dec = new ArrayList<Byte>();
        for (int i = 0; i+1 < list.size()-1; ) {    //end_HZ 1 빼기
            if(i < 4 && (list.get(i)==11 || list.get(i)==12) ){   //시작의 경우는 제외하도록 하였습니다.
                i += 1;
                continue;
            }
            if(list.get(i)<0){
                i += 1;
                continue;
            }
            byte front = (byte)(list.get(i).byteValue()<<4);
            dec.add(new Byte((byte)(list.get(i+1).byteValue() + front)));
            i += 2;
        }
        return dec;
    }

    private double findFrequency(double[] toTransform){
        int max = 0;
        int len = toTransform.length;
        double realNum;
        double imgNum;
        double[] mag = new double[len];

        //fourier transform을 이용해서 얻은 무게중심의 배열
        Complex[] complx = transform.transform(toTransform,TransformType.FORWARD);
        Double[] freq = this.fftfreq(complx.length,1);

        for(int i =0; i<complx.length;i++){
            realNum = complx[i].getReal();
            imgNum = complx[i].getImaginary();
            mag[i] = Math.sqrt((realNum*realNum)+(imgNum*imgNum));
            max = mag[i] > mag[max] ? i : max;
        }

        return freq[max];
    }

    private Double[] fftfreq(int length, int d) {
        Double Dou [] = new Double[length];

        for(int i = 0; i < length/2; i++)
            Dou[i] = (double)i/(double)(length*d);

        for (int i=0;i<length/2;i++)
            Dou[i+(length/2)] = -(double)((length/2)-i)/(double)(length*d);

        return Dou;
    }

}
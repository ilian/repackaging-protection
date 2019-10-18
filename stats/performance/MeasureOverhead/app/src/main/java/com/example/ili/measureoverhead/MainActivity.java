package com.example.ili.measureoverhead;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        bench();
    }

    private static void bench() {
        System.out.printf("BENCH_RUN=%d %d %d %d\n", EncryptedINC() - PlainINC(), EncryptedVirtualInvoke() - PlainVitrualInvoke(),
                EncryptedINC() - PlainINC(), EncryptedVirtualInvoke() - PlainVitrualInvoke());
    }

    public static void consumeInt(int a) { System.out.println("Consumed integer"); }


    public static long PlainINC() {
        int i = GetBeef.getBeef();
        long b = System.nanoTime();
        System.out.println("INC_PLAIN_START");
        if(GetBeef.getBeef() == i) {
            i++;
        }
        System.out.println("INC_PLAIN_END");
        long e = System.nanoTime();
        System.out.println("INC_PLAIN_TIME=" + (e - b));
        consumeInt(i);
        return e - b;
    }

    public static long EncryptedINC() {
        int i = GetBeef.getBeef();
        long b = System.nanoTime();
        System.out.println("INC_ENC_START");
        if(GetBeef.getBeef() == 0xbeef) {
            i++;
        }
        System.out.println("INC_ENC_END");
        long e = System.nanoTime();
        System.out.println("INC_ENC_TIME=" + (e - b));
        consumeInt(i);
        return e - b;
    }

    public static long PlainVitrualInvoke() {
        int i = GetBeef.getBeef();
        long b = System.nanoTime();
        System.out.println("VI_PLAIN_START");
        if(GetBeef.getBeef() == i) {
            consumeInt(i);
        }
        System.out.println("VI_PLAIN_END");
        long e = System.nanoTime();
        System.out.println("VI_PLAIN_TIME=" + (e - b));
        consumeInt(i);
        return e - b;
    }

    public static long EncryptedVirtualInvoke() {
        int i = GetBeef.getBeef();
        long b = System.nanoTime();
        System.out.println("VI_ENC_START");
        if(GetBeef.getBeef() == 0xbeef) {
            consumeInt(i);
        }
        System.out.println("VI_ENC_END");
        long e = System.nanoTime();
        System.out.println("VI_ENC_TIME=" + (e - b));
        consumeInt(i);
        return e - b;
    }
}

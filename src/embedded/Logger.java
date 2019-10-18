package embedded;

import android.widget.Toast;

public class Logger implements Runnable {

    private final String s;
    private final Toast toast;

    public Logger(String s, Toast toast){
        this.s = s;
        this.toast = toast;
    }
    @Override
    public void run() {
        toast.setText(s);
        toast.show();
    }
}

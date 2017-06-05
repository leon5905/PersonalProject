package yeohweizhu.truerng;

import android.content.Context;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by yeohw on 6/4/2017.
 */

public class Helper {
    //General save binary data to file function. This function is used to save data to the phone for analysis purpose.
    public static void SaveToFile(Context context, String fileName, byte[] byteArr){
        FileOutputStream fos = null;
        try {
            fos = context.openFileOutput(fileName, Context.MODE_PRIVATE);
            fos.write(byteArr);
            fos.close();
            System.out.println("Save File Complete");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }




}

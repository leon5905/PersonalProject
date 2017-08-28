package yeohweizhu.truerng;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.regex.Pattern;

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

    //Get Number of cores
    public static int GetNumberOfCores() {
        if(Build.VERSION.SDK_INT >= 17) {
            return Runtime.getRuntime().availableProcessors();
        }
        else {
            // Querying Linux file (old ways)
            return GetNumCoresOldPhones();
        }
    }

    /**
     * Gets the number of cores available in this device, across all processors.
     * Requires: Ability to peruse the filesystem at "/sys/devices/system/cpu"
     * @return The number of cores, or 1 if failed to get result
     */
    private static int GetNumCoresOldPhones() {
        //Private Class to display only CPU devices in the directory listing
        class CpuFilter implements FileFilter {
            @Override
            public boolean accept(File pathname) {
                //Check if filename is "cpu", followed by a single digit number
                if(Pattern.matches("cpu[0-9]+", pathname.getName())) {
                    return true;
                }
                return false;
            }
        }

        try {
            //Get directory containing CPU info
            File dir = new File("/sys/devices/system/cpu/");
            //Filter to only list the devices we care about
            File[] files = dir.listFiles(new CpuFilter());
            //Return the number of cores (virtual CPU devices)
            return files.length;
        } catch(Exception e) {
            //Default to return 1 core
            return 1;
        }
    }
}

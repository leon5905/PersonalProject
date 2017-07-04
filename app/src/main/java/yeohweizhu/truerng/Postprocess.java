package yeohweizhu.truerng;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by yeohw on 7/4/2017.
 */

public class Postprocess {
    public static byte[] Postprocess(byte[] byteArr){
        List<Integer> intValueList = new ArrayList<Integer>();
        List<Double> doubleValueList = new ArrayList<>();

        int min=0;
        int max=0;

        for (int i=0;i<byteArr.length;i+=4){
            //Every 32 bit
            int integerValue = byteArr[i]<<24 | byteArr[i+1]<<16 | byteArr[i+2]<<8 | byteArr[i+3] ;
            intValueList.add(integerValue);

            if (integerValue > max){
                max = integerValue;
            }
            else if (integerValue < min){
                min = integerValue;
            }
        }

        //Normalize to 0-1
        for (int i=0;i<intValueList.size();i++){
            double d = ((double)(intValueList.get(i)-min))/(max-min);
            doubleValueList.add(d);
        }

        byte[] result = new byte[doubleValueList.size()*6];

        for (int i=0;i<doubleValueList.size();i++){
            double tentMapResult = TentMap(doubleValueList.get(i),50,1.99);

            result[i*6] = (byte) ((Double.doubleToLongBits(tentMapResult)>>40)&0xff);
            result[i*6+1] = (byte) ((Double.doubleToLongBits(tentMapResult)>>32)&0xff);
            result[i*6+2] = (byte) ((Double.doubleToLongBits(tentMapResult)>>24)&0xff);
            result[i*6+3] = (byte) ((Double.doubleToLongBits(tentMapResult)>>16)&0xff);
            result[i*6+4] = (byte) ((Double.doubleToLongBits(tentMapResult)>>8)&0xff);
            result[i*6+5] = (byte) ((Double.doubleToLongBits(tentMapResult))&0xff);

        }

        return result;
    }

    public static double TentMap(double initValue, int iterNum, double controlParameter ){
        controlParameter=1.99;

        double returnValue = initValue;

        for (int i=0;i<iterNum-1;i++){
            returnValue = ( returnValue<0.5? controlParameter*returnValue: controlParameter*(1-returnValue) );
        }

        return returnValue;
    }
}

package yeohweizhu.truerng;

import android.os.Build;

import java.io.File;
import java.io.FileFilter;
import java.util.List;
import java.lang.Thread;
import java.util.regex.Pattern;

/**
 * Created by yeohw on 7/4/2017.
 */

public class ChaosTrueRNG {
    public static byte[] Preprocess(List<byte[]> yList,List<byte[]> uList,List<byte[]> vList){
        if (yList.size() !=2){
            throw new AssertionError();
        }

        byte[] y1,y2,u1,u2,v1,v2;

        y1 = yList.get(0);
        y2 = yList.get(1);
        u1 = uList.get(0);
        u2 = uList.get(1);
        v1 = vList.get(0);
        v2 = vList.get(1);

        //XOR in reversing order
        int j=y1.length-1;
        for(int i=0;i<y1.length;++i){
            y1[i] = (byte) (y1[i]^y2[j]);
            --j;
        }

        j=u1.length-1;
        for(int i=0;i<u1.length;++i){
            u1[i] = (byte) (u1[i]^u2[j]);
            v1[i] = (byte) (v1[i]^v2[j]);
            --j;
        }

        int returnByteLength = u1.length*4;
        byte[] resultByte = new byte[returnByteLength];

        //Interlace UV like this, Y U Y V Y U Y V...
        for (int i = 0; i < (u1.length*2); i++) {
            if (i % 2 == 0) {
                resultByte[i * 2] = y1[i];
                resultByte[i * 2 + 1] = u1[i / 2];
            } else {
                resultByte[i * 2] = y1[i];
                resultByte[i * 2 + 1] = v1[i / 2];
            }
        }

        return resultByte;
    }

    public static byte[] PostprocessParallel(byte[] byteArr){
        //Prep for thread creation
        int numberOfCores =  Helper.GetNumberOfCores();
//        numberOfCores=1; //Testing implementation //Should be removed at release

        //Postprocess preparation - Divide (Group) byte into group of 4 (int32)
        int[] valueList = new int[byteArr.length/4]; //Int32
        int valueListLength = byteArr.length - (byteArr.length%4);

        double[] doubleValueList = new double[valueList.length];
        byte[] result = new byte[doubleValueList.length*4]; //Final Result - Parallel compute result destination

        int temp =  (byteArr[0]<<24&0xff000000) | (byteArr[1]<<16&0xff0000) | (byteArr[2]<<8&0xff00) | (byteArr[3]&0xff);
        long min=temp; //Prevent overflow when max-min
        long max=temp; //Prevent overflow when max-min

        int valueListCounter =0;
        for (int i=4;i<valueListLength;i+=4){
            int integerValue  = (byteArr[i]<<24&0xff000000) | (byteArr[i+1]<<16&0xff0000) | (byteArr[i+2]<<8&0xff00) | (byteArr[i+3]&0xff);
            valueList[valueListCounter++]= integerValue;

            if (integerValue > max){
                max = integerValue;
            }
            else if (integerValue < min){
                min = integerValue;
            }
        }

        //Worker
        int workerSize = doubleValueList.length/numberOfCores;
        int workerStartPos =0;
        int workerEndLength=0; //Initialze, will be set to appropriate length at loop

        Thread[] threadArray = new Thread[numberOfCores];
        for (int i=0;i<numberOfCores;i++){
            if(i==numberOfCores-1) //Last core process the remaining
                workerEndLength = doubleValueList.length;
            else
                workerEndLength+=workerSize;

            Thread thread = (new Thread(new PostProcessRunnable(workerStartPos, workerEndLength, min, max, valueList, doubleValueList, result)));
            thread.start();
            threadArray[i] = thread;

            workerStartPos+=workerSize;
        }

        for(Thread t:threadArray){
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    private static class PostProcessRunnable implements Runnable{

        int[] valueList;
        double[] doubleValueList;
        byte[] computedArr;
        int start,endLength; //Start Index
        double diff;
        long min,max;

        //Start Index, Ended at Length th
        PostProcessRunnable(int start,int endLength,long min,long max,int[] sourceArr,double[] doubleValueList,byte[] computedArr){
            this.start = start;
            this.endLength = endLength;
            this.min = min;
            this.max = max;
            this.diff =1.0/(max-min);
            this.doubleValueList = doubleValueList;

            this.valueList= sourceArr;
            this.computedArr=computedArr;
        }

        @Override
        public void run() {
            final int numStateCCML = 6;
            double[] num = new double[numStateCCML];
            double [] numQuaterTent = new double[numStateCCML];

            int loopEnd= endLength-numStateCCML;
            for (int i=start;i<loopEnd;i+=numStateCCML){
                //Normalize to range 0-1
                num[0] = ((double)(valueList[i]-min))*(diff);
                num[1] = ((double)(valueList[i+1]-min))*(diff);
                num[2] = ((double)(valueList[i+2]-min))*(diff);
                num[3] = ((double)(valueList[i+3]-min))*(diff);
                num[4] = ((double)(valueList[i+4]-min))*(diff);
                num[5] = ((double)(valueList[i+5]-min))*(diff);

                //Main diffusion loop
                int loopStateComplete = numStateCCML/2;
                for (int j=0;j<loopStateComplete;j++) { //Number of iteration = l/2
                    //Precompute quater since it will be use twice
                    numQuaterTent[0] =  0.25*LogisticMapRunnable(num[0]);
                    numQuaterTent[1] =  0.25*LogisticMapRunnable(num[1]);
                    numQuaterTent[2] =  0.25*LogisticMapRunnable(num[2]);
                    numQuaterTent[3] =  0.25*LogisticMapRunnable(num[3]);
                    numQuaterTent[4] =  0.25*LogisticMapRunnable(num[4]);
                    numQuaterTent[5] =  0.25*LogisticMapRunnable(num[5]);

                    num[0] = numQuaterTent[1] + numQuaterTent[5] + 0.5*LogisticMapRunnable(num[0]);
                    num[1] = numQuaterTent[2] + numQuaterTent[0] + 0.5*LogisticMapRunnable(num[1]);
                    num[2] = numQuaterTent[3] + numQuaterTent[1] + 0.5*LogisticMapRunnable(num[2]);
                    num[3] = numQuaterTent[4] + numQuaterTent[2] + 0.5*LogisticMapRunnable(num[3]);
                    num[4] = numQuaterTent[5] + numQuaterTent[3] + 0.5*LogisticMapRunnable(num[4]);
                    num[5] = numQuaterTent[0] + numQuaterTent[4] + 0.5*LogisticMapRunnable(num[5]);
                }

                //Record down converted double
                doubleValueList[i] = num[0];
                doubleValueList[i+1] = num[1];
                doubleValueList[i+2] = num[2];
                doubleValueList[i+3] = num[3];
                doubleValueList[i+4] = num[4];
                doubleValueList[i+5] = num[5];

                //TODO dont break!!
//                break;
            }

            //Take care of the rest
            int counter=0;
            int indexCounter=loopEnd;
            while(indexCounter<endLength){
                num[counter] = ((double)(valueList[indexCounter]-min))*(diff);
                indexCounter++;
                counter++;
            }
            while (counter<numStateCCML){
                num[counter] =((double)(valueList[counter]-min))*(diff); //Reuse front number
                counter++;
            }
            //Main diffusion loop
            for (int j=0;j<numStateCCML/2;j++) {
                //Precompute quater since it will be use twice
                numQuaterTent[0] =  0.25*LogisticMapRunnable(num[0]);
                numQuaterTent[1] =  0.25*LogisticMapRunnable(num[1]);
                numQuaterTent[2] =  0.25*LogisticMapRunnable(num[2]);
                numQuaterTent[3] =  0.25*LogisticMapRunnable(num[3]);
                numQuaterTent[4] =  0.25*LogisticMapRunnable(num[4]);
                numQuaterTent[5] =  0.25*LogisticMapRunnable(num[5]);
                num[0] = numQuaterTent[1] + numQuaterTent[5] + 0.5*LogisticMapRunnable(num[0]) ;
                num[1] = numQuaterTent[2] + numQuaterTent[0] + 0.5*LogisticMapRunnable(num[1]) ;
                num[2] = numQuaterTent[3] + numQuaterTent[1] + 0.5*LogisticMapRunnable(num[2]) ;
                num[3] = numQuaterTent[4] + numQuaterTent[2] + 0.5*LogisticMapRunnable(num[3]) ;
                num[4] = numQuaterTent[5] + numQuaterTent[3] + 0.5*LogisticMapRunnable(num[4]) ;
                num[5] = numQuaterTent[0] + numQuaterTent[4] + 0.5*LogisticMapRunnable(num[5]) ;
            }
            counter=0;
            while(loopEnd<endLength){
                doubleValueList[loopEnd] = num[counter];
                loopEnd++;
                counter++;
            }

            //Final Result Conversion
            for (int i=start;i<endLength;i++) {
                //XOR
                double conversionNum = doubleValueList[i];
                int intNum = (int) (((Double.doubleToLongBits(conversionNum)>>>32) & 0xffffffff) ^ ((Double.doubleToLongBits(conversionNum) & 0xffffffff)));
                computedArr[i * 4+3] = (byte) (intNum & 0xff);
                computedArr[i * 4+2] = (byte) (intNum >>> 8 & 0xff);
                computedArr[i * 4+1] = (byte) (intNum >>> 16 & 0xff);
                computedArr[i * 4] = (byte) (intNum >>> 24 & 0xff);
            }
        }

        private int switchCase;
        private double LogisticMapRunnable(double initValue){ //Logistic Map
            //TODO Check it trigger how many time
            if (initValue<=0)
                initValue=0.01;

            double controlParameter;

            //Seven Cases
            switch (switchCase){
                case 0:
                    controlParameter=3.99;
                    switchCase = 1;
                    break;
                case 1:
                    controlParameter=3.98;
                    switchCase = 2;
                    break;
                case 2:
                    controlParameter=3.999;
                    switchCase = 3;
                    break;
                case 3:
                    controlParameter=3.96;
                    switchCase = 4;
                    break;
                case 4:
                    controlParameter=3.996;
                    switchCase = 5;
                    break;
                case 5:
                    controlParameter=3.97;
                    switchCase = 6;
                    break;
                case 6:
                    controlParameter=3.995;
                    switchCase = 0;
                    break;
//                case 7:
//                    controlParameter=3.992;
//                    switchCase = 8;
//                    break;
//                case 8:
//                    controlParameter=3.991;
//                    switchCase= 9;
//                    break;
//                case 9:
//                    controlParameter=3.993;
//                    switchCase= 10;
//                    break;
//                case 10:
//                    controlParameter=3.997;
//                    switchCase= 11;
//                    break;
//                case 11:
//                    controlParameter=3.989;
//                    switchCase= 12;
//                    break;
//                case 12:
//                    controlParameter=3.987;
//                    switchCase= 0;
//                    break;
                default:
                    controlParameter=3.983;
            }

            double returnValue = initValue;

            //50 Times
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));

            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));

            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));

            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));

            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));
            returnValue = ( controlParameter*returnValue*(1-returnValue));

            return returnValue;
        }
    }

    //Desinged for single thread
    public static byte[] Postprocess(byte[] byteArr){
        int[] valueList = new int[byteArr.length/4]; //Int32
        int valueListLength = byteArr.length - (byteArr.length%4);

        double[] doubleValueList = new double[valueList.length]; //Result

//        long temp = (byteArr[0]<<56&0x7f00000000000000L) | (byteArr[1]<<48&0x00ff000000000000L) | (byteArr[2]<<40&0x0000ff0000000000L) | (byteArr[3]<<32&0x000000ff00000000L) | (byteArr[4]<<24&0xff000000) | (byteArr[5]<<16&0xff0000) | (byteArr[6]<<8&0xff00) | (byteArr[7]&0xff);
        int temp =  (byteArr[0]<<24&0xff000000) | (byteArr[1]<<16&0xff0000) | (byteArr[2]<<8&0xff00) | (byteArr[3]&0xff);
        long min=temp; //Prevent overflow when max-min
        long max=temp; //Prevent overflow when max-min

        int valueListCounter =0;
        for (int i=4;i<valueListLength;i+=4){
            //Every 64 bits
//            long integerValue  = (byteArr[i]<<56&0x7f00000000000000L) | (byteArr[i+1]<<48&0x00ff000000000000L) | (byteArr[i+2]<<40&0x0000ff0000000000L) | (byteArr[i+3]<<32&0x000000ff00000000L) | (byteArr[i+4]<<24&0xff000000) | (byteArr[i+5]<<16&0xff0000) | (byteArr[i+6]<<8&0xff00) | (byteArr[i+7]&0xff) ;
            int integerValue  = (byteArr[i]<<24&0xff000000) | (byteArr[i+1]<<16&0xff0000) | (byteArr[i+2]<<8&0xff00) | (byteArr[i+3]&0xff);

            valueList[valueListCounter++]= integerValue;

            if (integerValue > max){
                max = integerValue;
            }
            else if (integerValue < min){
                min = integerValue;
            }
        }
        double diff = 1.0/(max-min);

        //CCML
        int numStateCCML = 8;
        double[] num = new double[numStateCCML];
        double [] numQuaterTent = new double[numStateCCML];

        int loopLength = doubleValueList.length-numStateCCML;
        for (int i=0;i<loopLength;i+=numStateCCML){ //Iterate through all available data
            //Assigning initial state

            //Normalize to range 0-1
            num[0] = ((double)(valueList[i]-min))*(diff);
            num[1] = ((double)(valueList[i+1]-min))*(diff);
            num[2] = ((double)(valueList[i+2]-min))*(diff);
            num[3] = ((double)(valueList[i+3]-min))*(diff);
            num[4] = ((double)(valueList[i+4]-min))*(diff);
            num[5] = ((double)(valueList[i+5]-min))*(diff);
            num[6] = ((double)(valueList[i+6]-min))*(diff);
            num[7] = ((double)(valueList[i+7]-min))*(diff);

            //Main diffusion loop
            for (int j=0;j<numStateCCML/2;j++) { //Number of iteration = l/2
                //Precompute quater since it will be use twice
                numQuaterTent[0] =  0.25*LogisticMap(num[0]);
                numQuaterTent[1] =  0.25*LogisticMap(num[1]);
                numQuaterTent[2] =  0.25*LogisticMap(num[2]);
                numQuaterTent[3] =  0.25*LogisticMap(num[3]);
                numQuaterTent[4] =  0.25*LogisticMap(num[4]);
                numQuaterTent[5] =  0.25*LogisticMap(num[5]);
                numQuaterTent[6] =  0.25*LogisticMap(num[6]);
                numQuaterTent[7] =  0.25*LogisticMap(num[7]);


                num[0] = 0.5*LogisticMap(num[0]) + numQuaterTent[1] + numQuaterTent[7];
                num[1] = 0.5*LogisticMap(num[1]) + numQuaterTent[2] + numQuaterTent[0];
                num[2] = 0.5*LogisticMap(num[2]) + numQuaterTent[3] + numQuaterTent[1];
                num[3] = 0.5*LogisticMap(num[3]) + numQuaterTent[4] + numQuaterTent[2];
                num[4] = 0.5*LogisticMap(num[4]) + numQuaterTent[5] + numQuaterTent[3];
                num[5] = 0.5*LogisticMap(num[5]) + numQuaterTent[6] + numQuaterTent[4];
                num[6] = 0.5*LogisticMap(num[6]) + numQuaterTent[7] + numQuaterTent[5];
                num[7] = 0.5*LogisticMap(num[7]) + numQuaterTent[0] + numQuaterTent[6];
            }

            //Record down converted double
            doubleValueList[i] = num[0];
            doubleValueList[i+1] = num[1];
            doubleValueList[i+2] = num[2];
            doubleValueList[i+3] = num[3];
            doubleValueList[i+4] = num[4];
            doubleValueList[i+5] = num[5];
            doubleValueList[i+6] = num[6];
            doubleValueList[i+7] = num[7];
        }

        //Final Conversion
        byte[] result = new byte[doubleValueList.length*4];
        for (int i=0;i<doubleValueList.length;i++) {

            //XOR
            double conversionNum = doubleValueList[i];
            int intNum = (int) (((Double.doubleToLongBits(conversionNum)>>>32) & 0xffffffff) ^ ((Double.doubleToLongBits(conversionNum) & 0xffffffff)));
            result[i * 4+3] = (byte) (intNum & 0xff);
            result[i * 4+2] = (byte) (intNum >>> 8 & 0xff);
            result[i * 4+1] = (byte) (intNum >>> 16 & 0xff);
            result[i * 4] = (byte) (intNum >>> 24 & 0xff);
        }

        return result;
    }

    private static double LogisticMap(double initValue){ //Logistic Map
        if (initValue<=0)
            initValue=0.01;

        double controlParameter =3.99;

        double returnValue = initValue;

        //53 times
        //10 batch
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));

        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));

        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));

        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));

        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));

        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));
        returnValue = ( controlParameter*returnValue*(1-returnValue));

//        for (int i=0;i<iterNum;i++){
//            returnValue = ( controlParameter*returnValue*(1-returnValue));
//        }

        return returnValue;
    }

    //LogisticMap proves to be better performance wise..
    private static double TentMap(double initValue){
        int iterNum = 50;
        double controlParameter=1.99;

        double returnValue = initValue;

        for (int i=0;i<iterNum;i++){
            returnValue = ( returnValue<0.5? controlParameter*returnValue: controlParameter*(1-returnValue) );
        }

        return returnValue;
    }
}

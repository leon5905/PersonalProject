package yeohweizhu.truerng;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by yeohw on 5/29/2017.qa
 */

public class ChaosMap {
    public static void TestFixedMap(){
        long elapsedTime;
        long startTime;

        startTime = System.nanoTime();
        for (int i=0;i<1;i++) {
            TentMap(0.5, 1000, 1.99);
        }
        elapsedTime = System.nanoTime() - startTime;
        System.out.println("Tent - Ms Taken: " + elapsedTime);


        startTime = System.nanoTime();
        for (int i=0;i<1;i++) {
            LogisticMap(0.5, 1000, 3.99);
        }
        elapsedTime = System.nanoTime() - startTime;
        System.out.println("Log - Ms Taken: " + elapsedTime);

        startTime = System.nanoTime();
        for (int i=0;i<1;i++) {
            TentMapFixed(0.5, 1000, 1.99);
        }
        elapsedTime = System.nanoTime() - startTime;
        System.out.println("Tent Fixed- Ms Taken: " + elapsedTime);


        startTime = System.nanoTime();
        for (int i=0;i<1;i++) {
            LogisticMapFixed(0.5, 1000, 3.99);
        }
        elapsedTime = System.nanoTime() - startTime;
        System.out.println("Log Fixed - Ms Taken: " + elapsedTime);

//        Fixed32[] ftent = TentMapFixed(0.5, 50, 1.99);
//        double[] dtent = TentMap(0.5, 50, 1.99);
//        for(int i=0;i<ftent.length;i++){
//            System.out.println("Tent Value Fixed " + i + "  : " + ftent[i].toDouble());
//            System.out.println("Tent Value Double "+ i + " : " + dtent[i]);
//        }

//        Fixed32[] flog = LogisticMapFixed(0.5, 50, 3.99);
//        double[] dlog = LogisticMap(0.5, 50, 3.99);
//        for(int i=0;i<flog.length;i++){
//            System.out.println("Log Value Fixed " + i + "  : " + flog[i].toDouble());
//            System.out.println("Log Value Double "+ i + " : " + dlog[i]);
//        }

//        LogisticMapFixed(0.5, 1000, 1.99);
    }

    public static void TestFixed(){
        System.out.println("Fixed Double: " + 6.25 );
        System.out.println("Fixed Fixed: " + Fixed32.fromDouble(6.25,24).toDouble());

        System.out.print("Fixed Add: 6.25 - 1 = " );
        System.out.println(Fixed32.fromDouble(6.25,24).Minus(Fixed32.fromDouble(1,24)).toDouble());

        System.out.print("Fixed Add: 6.25 + 1 = " );
        System.out.println(Fixed32.fromDouble(6.25,24).Add(Fixed32.fromDouble(1,24)).toDouble());

        System.out.print("Fixed Multiply: 6.25 * 2 = " );
        System.out.println(Fixed32.fromDouble(6.25,24).Multiply(Fixed32.fromDouble(2,24)).toDouble());

        System.out.print("Fixed Multiply: 6.25 / 2 = " );
        System.out.println(Fixed32.fromDouble(6.25,24).Divide(Fixed32.fromDouble(2,24)).toDouble());
    }

    public static void Test(){
        long elapsedTime;
        long startTime;

        startTime = System.nanoTime();
        for (int i=0;i<50;i++) {
            TentMap(0.5, 1000, 1.99);
        }
        elapsedTime = System.nanoTime() - startTime;
        System.out.println("Tent - Ms Taken: " + elapsedTime);


        startTime = System.nanoTime();
        for (int i=0;i<50;i++) {
            LogisticMap(0.5, 1000, 3.99);
        }
        elapsedTime = System.nanoTime() - startTime;
        System.out.println("Log - Ms Taken: " + elapsedTime);

//        for(double value:logDouble){
//            System.out.println("Value: " + value);
//        }
    }

    public static double[] TentMap(double initValue, int iterNum, double controlParameter ){
        controlParameter=1.99;

        double[] returnValue = new double[iterNum];
        returnValue[0] = initValue;

        for (int i=0;i<iterNum-1;i++){
            returnValue[i+1] = ( returnValue[i]<0.5? controlParameter*returnValue[i] : controlParameter*(1-returnValue[i]) );
        }

        return returnValue;
    }

    public static double[] LogisticMap(double initValue, int iterNum, double controlParameter ){
        controlParameter=3.99;

        double[] returnValue = new double[iterNum];
        returnValue[0] = initValue;

        for (int i=0;i<iterNum-1;i++){
            returnValue[i+1] = ( controlParameter*returnValue[i]*(1-returnValue[i]) );
        }

        return returnValue;
    }

    public static Fixed32[] TentMapFixed(double initValue, int iterNum, double controlParameter){
        int scale;

        controlParameter=1.99;

        Fixed32 initValueFixed = Fixed32.fromDouble(initValue,30);
        Fixed32 controlParameterFixed = Fixed32.fromDouble(controlParameter,30);
        Fixed32 zeroPoint5 = Fixed32.fromDouble(0.5,30);
        Fixed32 one = Fixed32.fromInt(1,30);

        Fixed32[] returnValue = new Fixed32[iterNum];
        returnValue[0] = initValueFixed;

        for (int i=0;i<iterNum-1;i++){
            returnValue[i+1] = ( returnValue[i].Compare(zeroPoint5)==-1? controlParameterFixed.Multiply(returnValue[i]) : controlParameterFixed.Multiply(one.Minus(returnValue[i])) );
        }

        return returnValue;
    }

    public static Fixed32[] LogisticMapFixed(double initValue, int iterNum, double controlParameter ){
        controlParameter=3.99;

        Fixed32 initValueFixed = Fixed32.fromDouble(initValue,30);
        Fixed32 controlParameterFixed = Fixed32.fromDouble(controlParameter,30);
        Fixed32 one = Fixed32.fromInt(1,30);

        Fixed32[] returnValue = new Fixed32[iterNum];
        returnValue[0] = initValueFixed;

        for (int i=0;i<iterNum-1;i++){
            returnValue[i+1] = ( controlParameterFixed.Multiply( returnValue[i].Multiply( one.Minus(returnValue[i]) ) ) );
        }

        return returnValue;
    }

}

package yeohweizhu.truerng;

/**
 * Created by yeohw on 5/29/2017.
 */

public class Fixed32 {
    final int x;
    final int scale;

    private Fixed32(int value, int scale){
        if (scale>29)
            this.scale= 29;
        else
            this.scale=scale;

        x = value;
    }

    public Fixed32 Add(Fixed32 operand){
        return new Fixed32(x+operand.x,scale);
    }

    public Fixed32 Minus(Fixed32 operand){
        return new Fixed32(x-operand.x,scale);
    }

    public Fixed32 Multiply(Fixed32 operand){
        return new Fixed32((int) ( ((long) x * (long) operand.x) >>scale ),scale);
    }

    public Fixed32 Divide(Fixed32 operand){
        return new Fixed32((int) ( ( ((long) x<<scale) / operand.x) ),scale);//If scale is 32 then this wont work.
    }

    public int Compare(Fixed32 operand){
        if (operand.x == this.x){
            return 0;
        }
        else if (this.x>operand.x){
            return 1;
        }
        else
            return -1;

    }

    //Conversion
    public double toDouble(){
        return (double) x / (double) (1<<scale);
    }

    public static Fixed32 fromDouble(double value, int scale){
        scale=29; //Currently does not support different scale

        return new Fixed32( (int) (value * (double) (1<<scale)),scale);
    }

    public static Fixed32 fromInt(int value, int scale){
        scale=29; //Currently does not support different scale

        if (value>1)
            value =1;

        return new Fixed32(value << scale,scale);
    }
}

package yeohweizhu.truerng;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.Image;
//import android.renderscript.RenderScript;
import android.support.v8.renderscript.*;

import java.nio.ByteBuffer;

/**
 * Created by yeohw on 5/29/2017.
 */

public class ImageFormatConverter {
    private static RenderScript sRenderScriptYuvToRgb;
    private static ScriptC_yuv420888 sYuv420;

    public static Bitmap YUV_420_888_toRGB(Image image, int width, int height, Context context){
        if (sRenderScriptYuvToRgb==null){
            sRenderScriptYuvToRgb =  RenderScript.create(context);
            sYuv420 = new ScriptC_yuv420888 (sRenderScriptYuvToRgb);
        }

        // Get the three image planes
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        byte[] y = new byte[buffer.remaining()];
        buffer.get(y);

        buffer = planes[1].getBuffer();
        byte[] u = new byte[buffer.remaining()];
        buffer.get(u);

        buffer = planes[2].getBuffer();
        byte[] v = new byte[buffer.remaining()];
        buffer.get(v);

        // get the relevant RowStrides and PixelStrides
        // (we know from documentation that PixelStride is 1 for y)
        int yRowStride= planes[0].getRowStride();
        int uvRowStride= planes[1].getRowStride();  // we know from   documentation that RowStride is the same for u and v.
        int uvPixelStride= planes[1].getPixelStride();  // we know from   documentation that PixelStride is the same for u and v.

        // Y,U,V are defined as global allocations, the out-Allocation is the Bitmap.
        // Note also that uAlloc and vAlloc are 1-dimensional while yAlloc is 2-dimensional.
        Type.Builder typeUcharY = new Type.Builder(sRenderScriptYuvToRgb, Element.U8(sRenderScriptYuvToRgb));
        typeUcharY.setX(yRowStride).setY(height);
        Allocation yAlloc = Allocation.createTyped(sRenderScriptYuvToRgb, typeUcharY.create());
        yAlloc.copyFrom(y);
        sYuv420.set_ypsIn(yAlloc);

        Type.Builder typeUcharUV = new Type.Builder(sRenderScriptYuvToRgb, Element.U8(sRenderScriptYuvToRgb));
        // note that the size of the u's and v's are as follows:
        //      (  (width/2)*PixelStride + padding  ) * (height/2)
        // =    (RowStride                          ) * (height/2)
        // but I noted that on the S7 it is 1 less...
        typeUcharUV.setX(u.length);
        Allocation uAlloc = Allocation.createTyped(sRenderScriptYuvToRgb, typeUcharUV.create());
        uAlloc.copyFrom(u);
        sYuv420.set_uIn(uAlloc);

        Allocation vAlloc = Allocation.createTyped(sRenderScriptYuvToRgb, typeUcharUV.create());
        vAlloc.copyFrom(v);
        sYuv420.set_vIn(vAlloc);

        // handover parameters
        sYuv420.set_picWidth(width);
        sYuv420.set_uvRowStride (uvRowStride);
        sYuv420.set_uvPixelStride (uvPixelStride);

        Bitmap outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Allocation outAlloc = Allocation.createFromBitmap(sRenderScriptYuvToRgb, outBitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);

        Script.LaunchOptions lo = new Script.LaunchOptions();
        lo.setX(0, width);  // by this we ignore the y’s padding zone, i.e. the right side of x between width and yRowStride
        lo.setY(0, height);

        sYuv420.forEach_doConvert(outAlloc,lo);
        outAlloc.copyTo(outBitmap);

        return outBitmap;
    }

    public static Bitmap RotateBitmap(Bitmap source, float angle)
    {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }
}

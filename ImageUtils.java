package com.mahzooz.monitor;
import android.graphics.Bitmap;import android.graphics.Rect;import android.media.Image;import java.nio.ByteBuffer;
public final class ImageUtils{private ImageUtils(){}public static Bitmap toBitmap(Image image){Image.Plane p=image.getPlanes()[0];ByteBuffer buf=p.getBuffer();int ps=p.getPixelStride(),rs=p.getRowStride(),w=image.getWidth(),h=image.getHeight(),pad=rs-ps*w;Bitmap b=Bitmap.createBitmap(w+pad/ps,h,Bitmap.Config.ARGB_8888);b.copyPixelsFromBuffer(buf);if(b.getWidth()!=w)b=Bitmap.createBitmap(b,new Rect(0,0,w,h));return b;}}

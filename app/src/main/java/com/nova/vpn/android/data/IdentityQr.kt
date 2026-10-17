package com.nova.vpn.android.data
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
object IdentityQr {
 fun bitmap(code:String):Bitmap {
  require(code.startsWith("NOVA-ID1:") && code.length<=1800) { "Для кода длиннее 1800 символов используйте файл." }
  val matrix=MultiFormatWriter().encode(code,BarcodeFormat.QR_CODE,768,768,mapOf(EncodeHintType.MARGIN to 4,EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
  val pixels=IntArray(768*768) {i->if(matrix[i%768,i/768]) 0xff000000.toInt() else 0xffffffff.toInt()}
  return Bitmap.createBitmap(pixels,768,768,Bitmap.Config.ARGB_8888)
 }
}

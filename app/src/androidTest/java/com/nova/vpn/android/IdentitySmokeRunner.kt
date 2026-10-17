package com.nova.vpn.android
import android.app.Instrumentation
import android.app.Activity
import android.os.Bundle
import com.nova.vpn.android.data.*
class IdentitySmokeRunner:Instrumentation() {
 private lateinit var args:Bundle
 override fun onCreate(arguments:Bundle?) {args=arguments?:Bundle();super.onCreate(arguments);start()}
 override fun onStart() {
  val result=Bundle()
  try {
   check(targetContext.packageName=="com.nova.vpn.android.identitypreview")
   if(args.getString("cleanup")=="true") {
    ProfileVault(targetContext).save(emptyList());IdentityVault(targetContext).save(IdentityState());IdentityVault(targetContext,true).save(IdentityState());SelectedProfileStore(targetContext).clear();AppRoutingStore(targetContext).save(AppRoutingSettings());AppPreferencesStore(targetContext).setAutoConnectEnabled(false)
    result.putString("stream","PASS isolated test fixtures cleared; production app untouched");finish(Activity.RESULT_OK,result);return
   }
   fun step(text:String) {sendStatus(1,Bundle().apply {putString("stream",text)})}
   AppPreferencesStore(targetContext).apply {setAutoConnectEnabled(false);setUpdateChecksEnabled(false);dismissTutorial()}
   startActivitySync(android.content.Intent(targetContext,MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
   step("started")
   val code=args.getString("code")?:error("Missing synthetic input")
   step("decrypt")
   val state=IdentityTransfer.import(code,"Local-test-only-2026")
   val p=state.forUrl("https://provider.example.test/sub/test")!!
   check(p.hwid=="0123456789abcdef0123456789abcdef" && p.userAgent=="NOVA-Test/1.0" && p.headers["X-Test-Token"]=="synthetic-not-a-secret")
   step("vault save")
   IdentityVault(targetContext,true).save(state)
   step("vault reopen")
   val restored=IdentityVault(targetContext,true).load()
   check(restored==state)
   check(runCatching {IdentityTransfer.import(code,"Incorrect-password")}.isFailure)
   check(runCatching {p.copy(headers=mapOf("Content-Length" to "9")).validate()}.isFailure)
   args.getString("full")?.let { full ->
    step("full import")
    val doc=FullTransfer.read(full,"Local-test-only-2026")
    FullTransfer.restore(targetContext,doc)
    check(ProfileVault(targetContext).load().single().id=="full-test-profile")
    check(AppRoutingStore(targetContext).load().sites.any {it.domain=="direct.example.test"})
    check(!AppPreferencesStore(targetContext).autoConnectEnabled())
    java.io.File(targetContext.getExternalFilesDir(null),"full-android.novaid").writeText(FullTransfer.export(targetContext,"Local-test-only-2026"))
    step("full export saved")
   }
   val windowsQr=java.io.File(targetContext.getExternalFilesDir(null),"windows-qr.png")
   if(windowsQr.exists()) {
    val image=android.graphics.BitmapFactory.decodeFile(windowsQr.absolutePath)
    val source=IntArray(image.width*image.height);image.getPixels(source,0,image.width,0,0,image.width,image.height)
    val value=com.google.zxing.MultiFormatReader().decode(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(com.google.zxing.RGBLuminanceSource(image.width,image.height,source))))
    check(value.text==code);step("Windows QR decoded on Android")
   }
   val qr=IdentityQr.bitmap(code)
   val pixels=IntArray(qr.width*qr.height);qr.getPixels(pixels,0,qr.width,0,0,qr.width,qr.height)
   val decoded=com.google.zxing.MultiFormatReader().decode(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(com.google.zxing.RGBLuminanceSource(qr.width,qr.height,pixels))))
   check(decoded.text==code)
   step("export")
   java.io.File(targetContext.getExternalFilesDir(null),"android.novaid").writeText(IdentityTransfer.export(restored,"Local-test-only-2026"))
   java.io.File(targetContext.getExternalFilesDir(null),"android-qr.png").outputStream().use {qr.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
   step("identity and QR saved")
   result.putString("stream","PASS Windows -> Android device; encrypted vault save/reopen; exact identity and bindings; wrong password and reserved header rejected; no VPN service invoked")
   finish(Activity.RESULT_OK,result)
  }catch(t:Throwable){result.putString("stream","FAIL: "+t.toString());finish(Activity.RESULT_CANCELED,result)}
 }
}




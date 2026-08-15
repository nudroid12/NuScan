# PdfBox-Android optionally supports JPX/JP2 images through JP2Android.
# NuScan does not bundle that legacy optional dependency, so R8 can safely
# ignore references to Gemalto JP2 classes. PdfBox logs a warning and skips
# JPX decoding when those optional classes are absent.
-dontwarn com.gemalto.jp2.**

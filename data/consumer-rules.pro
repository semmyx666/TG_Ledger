# Gson reads DTO fields reflectively, including after release shrinking.
-keep class com.semmyx.ledger.data.remote.RateDto { *; }
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault

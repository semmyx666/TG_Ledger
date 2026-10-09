# Retrofit / Gson consumer rules come from the data module and their libraries.
# Room and Compose ship their own shrinker rules. Do not globally keep the app.
-keepattributes Signature,InnerClasses,EnclosingMethod

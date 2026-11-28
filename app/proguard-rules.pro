# Regras do R8 da release. Room, WorkManager, Credentials e Play Services trazem as próprias regras.

# Cliente de APIs do Google (Drive): os modelos são preenchidos por reflection nos campos @Key
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}
-keep class com.google.api.services.drive.** { *; }
-keep class * extends com.google.api.client.json.GenericJson { *; }
-keep class com.google.api.client.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.appengine.**
-dontwarn javax.annotation.**
-dontwarn org.joda.time.**
-dontwarn sun.misc.Unsafe

# Login do Google pelo Credential Manager (a implementação do Play Services é carregada por reflection)
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }

# Mantém nomes de linha nos relatórios de erro
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ONNX Runtime: o JNI procura classes e métodos pelo nome
-keep class ai.onnxruntime.** { *; }

# LLM local (libmotor_llm.so): o JNI acha os métodos nativos e o callback pelo nome
-keepclasseswithmembernames class com.example.financacelular.ia.llm.MotorLlm { native <methods>; }
-keepclassmembers class com.example.financacelular.ia.llm.MotorLlm$Receptor { void pedaco(byte[]); }

# WorkManager cria o InputMerger por reflexão; o consumer rule do work-runtime 2.9.0 não preserva
# o construtor no R8 full mode, e sem isso nenhum Worker (download da IA, backup, lembrete) roda no release
-keepclassmembers class * extends androidx.work.InputMerger { public <init>(); }

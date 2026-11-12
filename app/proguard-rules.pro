# Velune R8 规则
#
# 开启 minify 后唯一真正的反射雷是 JSch：它按配置字符串反射加载
# com.jcraft.jsch.jce.* 与 keypair 实现，被裁掉时不会编译报错，
# 只在运行期表现为 "Algorithm negotiation fail"。
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# 崩溃栈要可读：保留行号，混淆文件名
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Compose / Kotlin 元数据：注解与签名信息在运行时仍被用到
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# 应用入口与四大组件由 AGP 默认规则保留，此处无需重复声明。
# 说明：项目不使用 Room、不使用 Kotlin 反射、不用 Gson/Moshi（JSON 走 org.json），
# 因此除 JSch 外没有额外的 keep 需求。若后续引入序列化库，规则加在这里。

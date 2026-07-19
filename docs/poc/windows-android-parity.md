# Windows / Android 話者推論 parity

## 目的と範囲

同じモデルとWAVをWindowsの直接ONNX Runtime経路とAndroidのsherpa-onnx経路へ入力し、ハッシュ、長さ、次元、score、判定を比較します。ライブマイク、VAD、区間化はこの試験に含めません。

Androidで現在比較できるのはCAM++ English、CAM++ Chinese-English、ERes2Netの3モデルです。WindowsのJVS本比較は5モデルですが、SpeakerNet-MとTitaNet-SはAndroid未統合です。

## 入力固定

| 用途 | bytes | SHA-256 |
|---|---:|---|
| Chinese enrollment | 73,606 | `33C24061180224D2350143EE19E3AF031446995C676BD25996325D34BB20A4D5` |
| Chinese same query | 178,374 | `9175E523081BF6A630CE72A55B05F92148EAAFAF58CBBBE743686CD81C50848E` |
| Chinese unknown query | 263,878 | `36CDA04EE4D10E38095DE73B77C99E8E7C54347232967A9CDE4CF54F7D496BAB` |

Lombard GRID Englishの1 enrollment + 2 queryも同じ方式で固定します。回収JSONは[`results/android-speaker-parity`](../../results/android-speaker-parity/)にあります。

## API 28 x86実測

2026-07-13にAPI 28 x86エミュレータで[`SpeakerModelBenchmarkTest.kt`](../../app/src/androidTest/java/com/example/pepper_person_id_poc/SpeakerModelBenchmarkTest.kt)を実行し、2 testsが成功しました。回収したJSONは次の12個です。

- CAM++ English / CAM++ Chinese-English / ERes2Net
- sherpa Chinese / Lombard GRID English
- same / unknown query

Chinese same queryのAndroid scoreはCAM++ English `0.5764364600`、ERes2Net `0.7939661145`です。Windows値 `0.5763694644`、`0.7940911055`との絶対差はどちらも`0.002`以内です。入力WAVとmodel SHA-256、sample rate、sample count、embedding dimensionもJSONで追跡できます。

## API 23 x86とPepper ARMv7の区別

x86 AARの`libonnxruntime.so`はAPI 23にない`__write_chk`を要求するため、API 23 x86ではロードに失敗します。API 28 x86の成功は、Pepperと異なるABI/API/CPU/メモリ環境での結果です。

Pepper向け`armeabi-v7a`はONNX Runtimeを`libsherpa-onnx-jni.so`へ静的リンクしており、x86とバイナリ構成が異なります。したがってAPI 28 x86の12 JSONはPepper ARMv7のparityやレイテンシ・メモリ性能の代替にはしません。Pepper実機で同じinstrumentationを実行し、JSONを回収するのが次のparityゲートです。

## 再実行

```powershell
$Serial = '<adb-serial>'
$Runner = 'com.example.pepper_person_id_poc.test/androidx.test.runner.AndroidJUnitRunner'

.\gradlew.bat -PtargetAbi=armeabi-v7a :app:assembleDebug :app:assembleDebugAndroidTest
adb -s $Serial install -r app\build\outputs\apk\debug\app-debug.apk
adb -s $Serial install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s $Serial shell am instrument -w -r `
  -e class com.example.pepper_person_id_poc.SpeakerModelBenchmarkTest `
  $Runner
```

Windows側の中国語固定値照合は[`verify-speaker-parity.ps1`](../../scripts/windows/verify-speaker-parity.ps1)で実行できます。このscriptはAndroidから回収したJSONの代替ではありません。

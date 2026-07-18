package com.example.pepper_person_id_poc.ui.navigation

enum class AppScreen(
    val title: String,
    val description: String,
) {
    Settings(
        title = "設定",
        description = "PoC機能と判定閾値を設定します",
    ),
    DeviceDiagnostics(
        title = "端末診断",
        description = "Pepperのカメラ、マイク、メモリ、ネットワークを確認します",
    ),
    ModelSelection(
        title = "モデル選択",
        description = "顔識別モデルと話者識別モデルを切り替えます",
    ),
    PersonRegistration(
        title = "人物登録",
        description = "人物ごとの顔特徴量と声特徴量を登録します（人数上限なし）",
    ),
    FaceIdentification(
        title = "顔識別",
        description = "顔検出、追跡、1対N識別を個別に確認します",
    ),
    SpeakerRegistration(
        title = "声登録",
        description = "人物の声特徴量を共通personIdへ登録します",
    ),
    SpeakerIdentification(
        title = "話者識別",
        description = "PCM録音、VAD、1対N話者識別を個別に確認します",
    ),
    AnonymousSpeakerIdentification(
        title = "未登録リアルタイム話者識別",
        description = "登録せずに同じ話者らしさを一時IDで識別します",
    ),
    Transcription(
        title = "音声認識",
        description = "日本語の短い発話を文字起こしします",
    ),
    FusionTest(
        title = "統合テスト",
        description = "顔識別と話者識別を統合します",
    ),
    ConversationHistory(
        title = "会話履歴",
        description = "誰が、いつ、何を話したかを確認します",
    ),
    BenchmarkResults(
        title = "測定結果",
        description = "処理時間、メモリ、エラーを確認します",
    ),
}

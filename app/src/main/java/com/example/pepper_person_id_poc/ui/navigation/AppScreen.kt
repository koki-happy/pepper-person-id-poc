package com.example.pepper_person_id_poc.ui.navigation

enum class AppScreen(
    val title: String,
    val description: String,
) {
    Settings(
        title = "設定",
        description = "モデル選択、機能起動、識別パラメータを管理します",
    ),
    DeviceDiagnostics(
        title = "端末診断",
        description = "端末のカメラ、マイク、メモリ、ネットワーク構成を表示します",
    ),
    ModelSelection(
        title = "モデル選択",
        description = "顔識別モデルと話者識別モデルを切り替えます",
    ),
    PersonRegistration(
        title = "人物登録",
        description = "personIdに顔特徴量と声特徴量を紐付けて登録します",
    ),
    FaceIdentification(
        title = "リアルタイム顔検出+登録人物識別",
        description = "追跡中の各顔を登録人物一覧と1対N照合し、複数顔を並列識別します",
    ),
    FaceIdentificationLearning(
        title = "リアルタイム顔検出+特徴量抽出",
        description = "未登録顔ごとに最大20特徴量を取得し、一時人物IDの重心を更新します",
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
        description = "発話特徴量をセッション内の一時話者IDと1対N照合します",
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

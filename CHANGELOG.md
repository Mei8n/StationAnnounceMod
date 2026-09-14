# 変更履歴

## 0.2.2-beta（開発中）

### 放送機能

- 放送中のworldへログイン・dimension移動・respawnしたプレイヤーを、終了済みパーツを飛ばした現在位置からactive sessionへ同期します。
- Speaker routingをプレイヤー移動やSpeaker変更へ追従するevent-driven方式に変更しました。
- 接近JSで`sam.arrival(seconds, AnnounceData)`を指定し、停止放送装置のSTOP後に到着案内を流せるようにしました。
- RS式とRTM停車検知式の駅名連呼ブロックを追加しました。駅名連呼JSは`ScriptType.STATION_NAME`（2）です。
- 啓発放送ブロックへDIRECT/SCRIPTモードと「RS入力時のみ動作」を追加しました。
- toggle型発車メロディで、OFF後の戸閉放送を残したまま再ONできる打ち返しに対応しました。

### JavaScriptとパック

- `getScriptType()`による用途判定を追加しました。未宣言の旧JSは`UNKNOWN`として互換利用できます。
- JavaScriptへ渡す`tile`をread-only snapshotとし、正式なNashorn APIとhost accessの境界を定めました。
- 通常放送のrepeat指定と、通常放送・戸閉放送内の`sam.interval(seconds)`に対応しました。
- SpeakerへPack内MQOモデル、検索・プレビュー、Yaw、XYZオフセットを設定できるようにしました。空のモデル名は従来どおり「モデルなし」です。
- Switch/SpeakerモデルのスムーズシェーディングとcullingをRTM方式へ合わせました。テクスチャ指定はJSONの`model.textures`だけを使用します。

### 安定性と負荷

- session lifecycleをserver側でも管理し、途中参加、client ACK、Awareness pause、Departure自然終了の関係を整理しました。
- server task queueへCRITICAL/NORMALを追加し、client packet queueをFIFO・1tick最大128件に制限しました。
- RTM列車検索をworld・game tick単位で共有する`TrainDetectionManager`へ集約しました。
- packet payload、UTF-8長、設定値、モデルresource pathの検証を強化しました。
- Java 8、Gradle 7.6.3、ForgeGradle 1.2の再現可能なbuildとrelease metadata検証を追加しました。

詳しい設定方法は、[通常放送・到着放送](howtoAddAnnounce.md)、[発車メロディ](howtoDepartureMelody.md)、[啓発放送](howtoAwarenessAnnouncer.md)、[駅名連呼](howtoStationNameAnnouncer.md)、[Speakerモデル](howtoSpeakerModels.md)、[JavaScript runtime](SCRIPT_RUNTIME.md)を参照してください。

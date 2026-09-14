# SAM JavaScript runtime仕様

この文書は、StationAnnounceMod（SAM）が正式にサポートする放送パック用JavaScript APIの境界を定めます。

## Runtimeとtrust model

SAMの正式runtimeは、Java 8に内蔵されたNashornです。SAM本体もJava 8を必要とします。各script fileは独立したengineへloadされ、同じengineの`samMain`が同時に実行されないよう同期されます。engine内のglobal変数が複数回の`samMain`呼び出し間で保持されることは、pack APIとして保証しません。

SAMpackのJavaScriptは、server管理者またはpack導入者が意図的に導入したtrusted contentとして扱います。clientやnetwork packetから受け取った文字列をevalする機能はありません。一方、これは悪意あるscriptを完全隔離するsecurity sandboxではありません。

`samMain`は論理server/game処理から同期的に呼び出され、その処理を完了までblockします。Java 8 Nashornに安全なhard execution timeoutはないため、SAMもhard timeoutを保証しません。scriptへ無限loop、`sleep`、重い計算を記述しないでください。worker thread化、`Thread.stop()`、interruptを強制停止とみなす処理は行いません。

## Host accessの境界

Nashorn engineはdeny-allの`ClassFilter`付きで生成され、`Java.type(...)`や`Packages...`によるJava class lookupを許可しません。`Java`、`Packages`、`JavaImporter`と標準package alias、および`load`、`loadWithNewGlobal`、`exit`、`quit`も無効です。

scriptへ渡す`tile`は実際のMinecraft `TileEntity`ではなく、呼び出し時点のread-only contextです。`World`、実TileEntity、`MinecraftServer`、network handler、mutableなSAM registryを取得する正式APIはありません。この制限はsupported APIを狭く保つためのもので、完全なsecurity sandboxを保証するものではありません。

## Global API

全scriptで次を使用できます。

- `sam`: SAMのscript API object
- `getDisplayName()`: 任意。設定画面用の名前を返します。省略時またはエラー時はscript filenameを使用します。最大256文字です。
- `samMain(tile)`: 必須。放送開始時に呼ばれます。

通常放送、駅名連呼、SCRIPTモードの啓発放送の`samMain`は`sam.build(startmelo, sounds, arrmelo[, repeatCount])`が返す`AnnounceData`を返す必要があります。駅名連呼と啓発放送にも親放送装置のread-only contextが渡されます。発車放送では`sam.build(melody, sounds, mode)`が返す`DepartureProgram`を返す必要があります。関数の欠落、`null`、異なる型、runtime error、不正またはpacket化できない出力はfail closedとなり、放送を開始しません。logにはscript名、`load` / `getDisplayName` / `samMain` phase、原因が記録されます。

`getScriptType()`は任意です。未宣言のscriptは`UNKNOWN`として後方互換で使用でき、既知の型を宣言したscriptは、要求する実行系と一致する場合だけGUI選択・server設定・実行が許可されます。整数IDは次のとおりです。

| ScriptType | ID | 使用先 |
| --- | ---: | --- |
| `UNKNOWN` | -1 | 型未宣言の旧script。各実行系で使用可能 |
| `APPROACH` | 0 | 接近放送 |
| `ARRIVAL` | 1 | 将来の独立到着放送用予約ID。停止後到着では使用しない |
| `STATION_NAME` | 2 | 駅名連呼 |
| `DEPARTURE_MELODY` | 3 | 発車メロディ |
| `AWARENESS` | 4 | 啓発放送 |

接近JSでは追加で `sam.build(start, sounds, loop, sam.arrival(seconds, data))` が返す `ApproachProgram` を使用できます。接近と到着内容を初回実行時にsnapshotし、停止放送装置のSTOP後に0～3600秒待機して到着を再生します。詳細は[停止後の到着放送](howtoAddAnnounce.md#停止後に到着放送を流す)を参照してください。

正式にサポートする`sam` APIは次のとおりです。

- `sam.startmelo(soundId)`
- `sam.arrmelo(soundId)`
- `sam.interval(seconds)`
- `sam.build(...)`
- `sam.arrival(seconds, AnnounceData)`（接近JSの到着予約用）
- `sam.push()`
- `sam.toggle()`
- departure modeの`.interval(seconds)`と`.tachikawa(boolean)`

音声IDは最大256文字、通常放送・戸閉放送は無音区間を含め最大256パーツ、通常放送のrepeat countは1～100です。詳細な引数と再生順は[通常放送ガイド](howtoAddAnnounce.md)と[発車メロディガイド](howtoDepartureMelody.md)を参照してください。

## `tile` context

通常放送、駅名連呼、SCRIPTモードの啓発放送では次を使用できます。

```javascript
tile.getLinkKey()
tile.linkKey
tile.receivedData.get("key")
```

`receivedData`は`samMain`呼び出し時点のsnapshotで、`get`、`containsKey`、`isEmpty`、`size`などのread-only queryを使用できます。変更操作は実TileEntityのdataへ反映されません。駅名連呼と啓発放送のcontextもLink Keyで接続した親放送装置から作られます。

発車放送では`tile.getLinkKey()`と`tile.linkKey`を使用できます。発車用contextに`receivedData`はありません。

従来、undocumentedな実TileEntity method、`World`、任意Java interopへ到達していたpackは、この正式仕様化により動作しなくなる場合があります。既存の公式documentに記載していた`sam` API、`tile.getLinkKey()`、`tile.linkKey`、通常放送の`tile.receivedData.get(...)`は維持されます。

実行系ごとの設定と例は、[通常放送ガイド](howtoAddAnnounce.md)、[駅名連呼ガイド](howtoStationNameAnnouncer.md)、[啓発放送ガイド](howtoAwarenessAnnouncer.md)、[発車メロディガイド](howtoDepartureMelody.md)を参照してください。

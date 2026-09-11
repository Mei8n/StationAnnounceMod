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

通常放送の`samMain`は`sam.build(startmelo, sounds, arrmelo[, repeatCount])`が返す`AnnounceData`を返す必要があります。発車放送では`sam.build(melody, sounds, mode)`が返す`DepartureProgram`を返す必要があります。関数の欠落、`null`、異なる型、runtime error、不正またはpacket化できない出力はfail closedとなり、放送を開始しません。logにはscript名、`load` / `getDisplayName` / `samMain` phase、原因が記録されます。

正式にサポートする`sam` APIは次のとおりです。

- `sam.startmelo(soundId)`
- `sam.arrmelo(soundId)`
- `sam.interval(seconds)`
- `sam.build(...)`
- `sam.push()`
- `sam.toggle()`
- departure modeの`.interval(seconds)`と`.tachikawa(boolean)`

音声IDは最大256文字、通常放送・戸閉放送は無音区間を含め最大256パーツ、通常放送のrepeat countは1～100です。詳細な引数と再生順は[通常放送ガイド](howtoAddAnnounce.md)と[発車メロディガイド](howtoDepartureMelody.md)を参照してください。

## `tile` context

通常放送では次を使用できます。

```javascript
tile.getLinkKey()
tile.linkKey
tile.receivedData.get("key")
```

`receivedData`は`samMain`呼び出し時点のsnapshotで、`get`、`containsKey`、`isEmpty`、`size`などのread-only queryを使用できます。変更操作は実TileEntityのdataへ反映されません。

発車放送では`tile.getLinkKey()`と`tile.linkKey`を使用できます。発車用contextに`receivedData`はありません。

従来、undocumentedな実TileEntity method、`World`、任意Java interopへ到達していたpackは、この正式仕様化により動作しなくなる場合があります。既存の公式documentに記載していた`sam` API、`tile.getLinkKey()`、`tile.linkKey`、通常放送の`tile.receivedData.get(...)`は維持されます。

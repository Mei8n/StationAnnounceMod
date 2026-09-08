# 追加パックについて

本Modは、RealTrainModの追加アドオンのような形式で、放送パーツと放送スクリプトを導入することができます。

## 導入方法

本Modを導入して起動するとmodsフォルダにSAMpacksというフォルダが生成されるので、その中にファイルを配置します。

親の放送装置の設定画面には、使用するJSのファイル名を拡張子 `.js` 付きで入力します。通常放送用・発車メロディ用のJSは、どちらもパック内の `assets/stationannouncemod/scripts/` に配置してください。

## パック作成方法

ファイル階層は以下の通りです。

```text
(パック名)
  assets
    stationannouncemod
      scripts
        jsファイル
    sound_(任意の名前)
      sounds
        音声パーツ
      sam_length.json
      sounds.json
```

### sounds.jsonの記法

リソースパックやRTM用追加アドオンのものと全く同じです。

[こちら](https://akikawaken.github.io/RTM/howto/ht_sounds.json.html)を参考にするか、[RTM Sound File Generator](https://hi03s.com/)をご利用ください。

### sam_length.jsonの記法

sounds.jsonで登録した名前と、そのファイルの秒数(小数第2位まで有効)を記入します。

発車メロディ・戸閉放送の各パーツの長さも、このファイルへの登録が必須です。発車用JSは `sam.build(melody, sounds, mode)` にメロディ音源ID・戸閉放送のパーツ配列・再生モードを渡します。長さが未登録または不正な場合は再生されず、対象の音源IDを含むエラーが表示されます。開始前インターバルは再生モードの `.interval(秒)`、戸閉放送のパーツ間隔は `sounds.push(sam.interval(秒))` で指定します。`sam.interval()` は音源ではないため、このJSONへの登録は不要です。詳しくは[発車メロディの説明](howtoDepartureMelody.md)を参照してください。

```json
{
  "sound_sample:hoge": {
    "length": 1.23
  },
  "sound_sample:fuga": {
    "length": 4.50
  }
}
```

sam_length.jsonの作成には、是非[こちら](https://raw.githubusercontent.com/Mei8n/zatta/refs/heads/main/get_length.py)をご活用ください。

このファイルと同階層に音声ファイルを配置して実行することで、音声ファイルの長さを取得してsam_length.jsonを自動生成してくれます。

## スクリプトの記法

### 接近放送の関数

#### getDisplayName()

- 戻り値: String

スクリプトの表示名を定義する省略可能な関数です。親の放送装置・発車メロディ装置の設定画面で、JSファイル名入力欄の下に表示されます。入力したファイル名に応じて表示が切り替わります。長い表示名は省略され、マウスを重ねると全文を確認できます。関数がない場合や表示名が空の場合はファイル名を表示します。

#### samMain(tile)

- 引数: tile (`TileEntityAnnouncer` インスタンス)
- 戻り値: sam.build() で生成された放送データ

自動実行されるイベントハンドラ関数です。この関数内にスクリプトを記述してください。

`samMain(tile)` は必須です。接近放送用JSでは `TileEntityAnnouncer`、発車メロディ用JSでは `TileEntityDepartureMelody` が渡されます。

### sam オブジェクト

#### sam.startmelo(soundId)

放送冒頭のメロディ用メソッドです。

#### sam.arrmelo(soundId)

放送が終わった後に鳴る、接近メロディ用メソッドです。

このメソッドで定義した音声は自動的にループし、放送停止でループが終了します。

#### sam.build(startmelo, sounds, arrmelo[, repeatCount])

```javascript
sam.build(startmelo, sounds, arrmelo)
sam.build(startmelo, sounds, arrmelo, repeatCount)
```

- 引数:
  - startmelo: sam.startmelo() の戻り値 (ない場合は null)
  - sounds: 再生する音声を順番に入れた配列 (Array)
  - arrmelo: sam.arrmelo() の戻り値 (ない場合は null)
  - repeatCount: `startmelo + sounds` を繰り返す回数 (省略可能、既定値1)
- 戻り値: 放送データ

ビルドメソッドです。samMain 関数は最後に必ずこれをreturnする必要があります。

メロディを省略したい場合は、その引数に `null` を直接渡してください。

4引数形式では、`startmelo` と `sounds` を1回分の通常放送として、全体を `repeatCount` 回再生します。`arrmelo` は繰り返しの対象ではなく、すべての通常放送が終了した後に従来通りループします。3引数形式は `repeatCount` に1を指定した場合と同じです。

`repeatCount` が0以下の場合は1に補正され、上限は100回です。`startmelo` が `null` なら `sounds` だけが繰り返され、`sounds` が空なら開始メロディだけが繰り返されます。`arrmelo` が `null` の場合は、指定回数の通常放送を再生した時点で終了します。開始メロディを `sounds` に手動で追加する必要はありません。

### tile オブジェクト

samMain(tile) の引数として渡されるオブジェクトで、列車選別装置から送信されたdataMapを取得するために使用します。

#### tile.receivedData.get(key)

- 引数: key (文字列) - 設定したdataMap キー名
- 戻り値: データ（通常は文字列型 String として取得されます）

設定されたキーに対応するデータを取得します。

受信データがまだ無い場合は `null` が返ります。

数値として条件分岐（if や switch）に使いたい場合は、JavaScript標準の parseInt() などで変換してください。

### サンプルスクリプト

以下は接近放送のサンプルです。音源IDの `example:*` をパックで登録したIDへ置き換え、JSファイルとしてパックZIPの `assets/stationannouncemod/scripts/` に入れてください。

発車メロディ用JSも `getDisplayName()` と `samMain(tile)` を使いますが、`tile` は `TileEntityDepartureMelody` です。戸閉放送を `sounds.push("音源ID")` で組み立て、パーツ間の無音は `sounds.push(sam.interval(秒数))` で指定します。最後に `sam.build(melody, sounds, mode)` を返してください。再生モードは `sam.push()` または `sam.toggle()` です。詳しい例は[発車メロディの説明](howtoDepartureMelody.md)を参照してください。

```javascript
function getDisplayName() {
    return "サンプル 接近放送（2回放送）";
}

function samMain(tile) {
    var startmelo = sam.startmelo("example:approach_chime");

    var sounds = [];
    sounds.push("example:train_approaching");
    sounds.push("example:platform_1");
    sounds.push("stationannouncemod:mute_0.25s");
    sounds.push("example:please_stand_behind_yellow_line");

    var arrmelo = sam.arrmelo("example:approach_melody");

    // startmelo + sounds を2回再生してから、arrmeloをループします。
    return sam.build(startmelo, sounds, arrmelo, 2);
}
```

### 発車メロディ用JSとの違い

どちらも `getDisplayName()`、`samMain(tile)`、`sounds.push(...)`、`sam.build(...)` を共通して使います。`sam.build(...)` の第1・第3引数と、パーツ間隔の指定方法が異なります。

| 項目 | 接近放送 | 発車メロディ |
| --- | --- | --- |
| `sam.build()` 第1引数 | `sam.startmelo(id)` または `null` | メロディの音源ID |
| 第2引数 | 接近放送の音声パーツ | 戸閉放送の音声パーツと `sam.interval()` |
| 第3引数 | `sam.arrmelo(id)` または `null` | `sam.push()` または `sam.toggle()` |
| 第4引数 | 通常放送の繰り返し回数（省略時1） | 使用しない |
| パーツ間の無音 | `stationannouncemod:mute_0.25s` などの無音音源 | `sam.interval(秒数)` |
| `tile` | `TileEntityAnnouncer` | `TileEntityDepartureMelody` |

接近放送の `arrmelo` は本放送後にループし、放送停止で終了します。発車メロディでは選択したモードが、単発再生かON中のループ再生かを決めます。`sam.interval()` は発車メロディの戸閉放送配列専用です。

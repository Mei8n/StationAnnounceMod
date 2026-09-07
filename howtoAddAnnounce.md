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

発車メロディ・戸閉放送の長さも、このファイルへの登録が必須です。発車用JSは `.melody("音源ID")`・`.doorClose("音源ID")` の形式とし、秒数引数は指定しません。長さが未登録または不正な場合は再生されず、対象の音源IDを含むエラーが表示されます。インターバルは引き続きJSの `.interval(秒)` で指定します。詳しくは[発車メロディの説明](howtoDepartureMelody.md)を参照してください。

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

### 必須関数

#### getDisplayName()

- 戻り値: String

スクリプトの表示名を定義する関数です。親の放送装置・発車メロディ装置の設定画面で、JSファイル名入力欄の下に表示されます。入力したファイル名に応じて表示が切り替わります。長い表示名は省略され、マウスを重ねると全文を確認できます。関数がない場合や表示名が空の場合はファイル名を表示します。

#### samMain(tile)

- 引数: tile (TileEntityAnnouncer インスタンス)
- 戻り値: sam.build() で生成された放送データ

自動実行されるイベントハンドラ関数です。この関数内にスクリプトを記述してください。

### sam オブジェクト

#### sam.startmelo(soundId)

放送冒頭のメロディ用メソッドです。

#### sam.arrmelo(soundId)

放送が終わった後に鳴る、接近メロディ用メソッドです。

このメソッドで定義した音声は自動的にループし、放送停止でループが終了します。

#### sam.build(startmelo, sounds, arrmelo)

- 引数:
  - startmelo: sam.startmelo() の戻り値 (ない場合は null)
  - sounds: 再生する音声を順番に入れた配列 (Array)
  - arrmelo: sam.arrmelo() の戻り値 (ない場合は null)
- 戻り値: 放送データ

ビルドメソッドです。samMain 関数は最後に必ずこれをreturnする必要があります。

メロディを省略したい場合は、その引数に `null` を直接渡してください。

### tile オブジェクト

samMain(tile) の引数として渡されるオブジェクトで、列車選別装置から送信されたdataMapを取得するために使用します。

#### tile.receivedData.get(key)

- 引数: key (文字列) - 設定したdataMap キー名
- 戻り値: データ（通常は文字列型 String として取得されます）

設定されたキーに対応するデータを取得します。

受信データがまだ無い場合は `null` が返ります。

数値として条件分岐（if や switch）に使いたい場合は、JavaScript標準の parseInt() などで変換してください。

### サンプルスクリプト

```javascript
function getDisplayName() {
    return "簡易放送";
}

function samMain(tile) {
    var startmelo = sam.startmelo("sound_sample:melody1");

    var sounds = [];
    sounds.push("sound_sample:parts1");
    sounds.push("sound_sample:parts2");
    sounds.push("sound_sample:parts3");

    var arrmelo = sam.arrmelo("sound_sample:melody2");

    return sam.build(startmelo, sounds, arrmelo);
}
```

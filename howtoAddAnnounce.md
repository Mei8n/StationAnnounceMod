# 追加パックについて
本Modは、RealTrainModの追加アドオンのような形式で、放送パーツと放送スクリプトを導入することができます。

## 導入方法
本Modを導入して起動するとmodsフォルダにSAMpacksというフォルダが生成されるので、その中にファイルを配置します。

## パック作成方法
ファイル階層は以下の通りです。

* (パック名)
  * assets
    * stationannouncemod
      * scripts
        * jsファイル
    * sound_(任意の名前)  
      * sounds
        * 音声パーツ
      * sam_length.json
      * sounds.json

### sounds.jsonの記法
リソースパックやRTM用追加アドオンのものと全く同じです。
[こちら](https://akikawaken.github.io/RTM/howto/ht_sounds.json.html)を参考にするか、[RTM Sound File Generator](https://hi03s.com/)をご利用ください。

### sam_length.jsonの記法
sounds.jsonで登録した名前と、そのファイルの秒数(小数第2位まで有効)を記入します。
```
{
  "sound_sample:hoge": {
   "length": 1.23
  },
  "sound_sample:fuga": {
   "length": 4.50
  }
}
```

## スクリプトの記法
### 必須関数
#### getDisplayName()
- 戻り値: String\
スクリプト名を定義する関数です。分かりやすい名前をつけてください。

#### samMain(tile)
- 引数: tile (TileEntityAnnouncer インスタンス)\
- 戻り値: sam.build() で生成された放送データ\
自動実行されるイベントハンドラ関数です。この関数内にスクリプトを記述してください。

### sam オブジェクト
#### sam.startmelo(soundId)
放送冒頭のメロディ用メソッドです。

#### sam.arrmelo(soundId)
放送が終わった後に鳴る、接近メロディ用メソッドです。 \
このメソッドで定義した音声は自動的にループし、放送停止でループが終了します。

#### sam.build(startmelo, sounds, arrmelo)
- 引数:
startmelo: sam.startmelo() の戻り値 (ない場合は null)\
sounds: 再生する音声を順番に入れた配列 (Array)\
arrmelo: sam.arrmelo() の戻り値 (ない場合は null)

- 戻り値: 放送データ

ビルドメソッドです。\
samMain 関数は最後に必ずこれをreturnする必要があります。\
メロディを省略したい場合は、その引数に `null` を直接渡してください。

### tile オブジェクト
samMain(tile) の引数として渡されるオブジェクトで、列車選別装置から送信されたdataMapを取得するために使用します。

#### tile.receivedData.get(key)
- 引数: key (文字列) - 設定したdataMap キー名
- 戻り値: データ（通常は文字列型 String として取得されます）

設定されたキーに対応するデータを取得します。\
受信データがまだ無い場合は `null` が返ります。\
数値として条件分岐（if や switch）に使いたい場合は、JavaScript標準の parseInt() などで変換してください。

### サンプルスクリプト
```
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

# 放送パック・通常放送JSの作成

放送パック作成ガイドです。音声ファイルをJSONで登録し、JavaScript（JS）で再生順を指定します。

導入・ブロックの操作は[README](README.md)、発車メロディ用JSとスイッチモデルは[発車メロディの設定](howtoDepartureMelody.md)を参照してください。

## パックの構成

パックはZIP形式にして`mods/SAMpacks`へ入れます。ZIPを開いた直下に`assets`が来るように圧縮してください。

```text
SamplePack.zip
  assets/stationannouncemod/scripts/approach.js
  assets/sound_sample/sounds.json
  assets/sound_sample/sam_length.json
  assets/sound_sample/packname/chime.ogg
  assets/sound_sample/packname/approaching.ogg
  assets/sound_sample/packname/platform_1.ogg
  assets/sound_sample/packname/approach_melody.ogg
```

| ファイル | 役割 |
| --- | --- |
| `scripts/*.js` | 放送内容と再生順を指定 |
| `sounds/*.ogg` | 音声・メロディのパーツ |
| `sounds.json` | 音声IDとOGGファイルの対応を登録 |
| `sam_length.json` | 音声IDごとの再生時間を登録 |

JS・JSONはUTF-8で保存します。`sound_sample`は音源を区別する名前（名前空間）で、パック独自の名前に変更できます。JSの配置先は`assets/stationannouncemod/scripts/`です。

JSは通常放送用・発車メロディ用ともファイル名で選択されるため、用途やパックごとに重複しない名前を付けてください。異なるZIPに同名JSがある場合、ZIPファイル名順で後から読み込まれたものが使われます。

## 音声を登録する

### sounds.json

`assets/sound_sample/sounds.json`に、次のように記述します。

### sounds.json

リソースパックやRTM用追加アドオンのものと全く同じです。

[こちら](https://akikawaken.github.io/RTM/howto/ht_sounds.json.html)を参考にするか、[RTM Sound File Generator](https://hi03s.com/)をご利用ください。

### sam_length.json

同じ名前空間の`sam_length.json`に、**音声IDと実際の長さ（秒）**を記述します。

```json
{
  "sound_sample:chime": { "length": 2.5 },
  "sound_sample:approaching": { "length": 1.8 },
  "sound_sample:platform_1": { "length": 1.2 },
  "sound_sample:approach_melody": { "length": 12.8 }
}
```

上の数値は例です。用意した音源の長さに置き換えてください。登録できる長さは0秒より大きく3600秒以下です。再生の切り替えは通常0.05秒単位に切り上げられます。

SAMはこの長さを使って次のパーツへ進みます。短すぎると音声が切れたり重なったりし、長すぎると余分な間ができます。開始メロディ・放送パーツ・接近メロディ・発車メロディ・戸閉放送・啓発放送に使う音源を登録してください。発車用の音源に未登録・不正な長さがある場合は、再生時にエラーになります。

## 通常放送のJS

### 基本例

次の内容を`approach.js`として保存します。前述の音声登録例と組み合わせて使用できます。

```javascript
function getDisplayName() {
    return "サンプル・1番線接近放送";
}

function samMain(tile) {
    var startmelo = sam.startmelo("sound_sample:chime");

    var sounds = [];
    sounds.push("sound_sample:approaching");
    sounds.push("sound_sample:platform_1");
    sounds.push(sam.interval(0.25));

    var arrmelo = sam.arrmelo("sound_sample:approach_melody");
    return sam.build(startmelo, sounds, arrmelo);
}
```

この例では、チャイム、接近案内、番線案内、0.25秒の無音の順に再生し、その後は接近メロディをループします。ループは放送停止ブロックなどで止めます。

### 関数と再生順

| 記述 | 役割 |
| --- | --- |
| `getDisplayName()` | 設定画面に表示する名前を返す。省略時はファイル名 |
| `samMain(tile)` | 放送開始時に呼ばれる関数。最後に`sam.build(...)`を返す |
| `sam.startmelo("音声ID")` | 冒頭のメロディ |
| `sounds.push("音声ID")` | 配列の末尾に音声を追加。追加した順に再生 |
| `sounds.push(sam.interval(秒数))` | 配列のその位置に任意の長さの無音区間を追加 |
| `sam.arrmelo("音声ID")` | 本放送の終了後、停止操作までループするメロディ |

通常放送は次の形で組み立てます。

```javascript
return sam.build(startmelo, sounds, arrmelo);
return sam.build(startmelo, sounds, arrmelo, repeatCount);
```

| 引数 | 内容 |
| --- | --- |
| `startmelo` | `sam.startmelo(...)`の戻り値。省略する場合は`null` |
| `sounds` | 本放送の音声IDと`sam.interval(...)`を再生順に入れた配列。音声と無音区間を合わせて最大256パーツ |
| `arrmelo` | `sam.arrmelo(...)`の戻り値。省略する場合は`null` |
| `repeatCount` | 冒頭メロディ＋本放送の再生回数。1～100の整数、省略時1 |

メロディを付けずに放送パーツだけを再生する場合は、次のように指定します。

```javascript
return sam.build(null, sounds, null);
```

### 冒頭メロディを含めて繰り返す

第4引数で1回分の放送全体を繰り返せます。

```javascript
return sam.build(startmelo, sounds, arrmelo, 2);
```

再生順は「冒頭メロディ → 本放送 → 冒頭メロディ → 本放送 → 接近メロディのループ」です。`arrmelo`を`null`にすると、指定回数の本放送を終えて停止します。

### パーツ間に間を入れる

`sam.interval(秒数)`を`sounds`へ追加します。秒数には任意の数値を指定でき、その位置に無音区間が入ります。

```javascript
sounds.push("sound_sample:approaching");
sounds.push(sam.interval(0.75));
sounds.push("sound_sample:platform_1");
```

指定範囲は0.01～3600秒です。小数第3位以下を切り捨て、20tick/秒に換算するときは切り上げます。

## 列車データで放送を変える（RTM連携）

列車選別装置で指定したdataMapは、通常放送の`samMain(tile)`内で次のように取得します。

```javascript
var value = tile.receivedData.get("キー名");
```

値は文字列として受け取ります。未受信のキーは`null`になるため、数値で比較する場合は未受信時の処理と数値変換を入れます。

以下は種別IDが100以上なら通過、100未満なら停車として放送パーツを選ぶ例です。`trainType`というキー名と判定値100は例なので、車両側の仕様に合わせて変更してください。例に登場する各音声IDは、パックへの登録が必要です。

```javascript
function getDisplayName() {
    return "列車種別に応じた接近放送";
}

function samMain(tile) {
    var sounds = [];
    var raw = tile.receivedData.get("trainType");
    var typeId = raw == null ? NaN : parseInt(String(raw), 10);

    if (isNaN(typeId)) {
        sounds.push("sound_sample:train_approaching");
    } else if (typeId >= 100) {
        sounds.push("sound_sample:train_passing");
    } else {
        sounds.push("sound_sample:train_arriving");
    }

    return sam.build(null, sounds, null);
}
```

文字列を比較する場合は`String(value)`、小数は`parseFloat(...)`を使えます。真偽値は文字列の`"true"`／`"false"`として比較します。

受信したデータは通常放送のJSを実行した後にクリアされます。列車選別装置がデータを渡してから放送開始ブロックが動作するように配置してください。同じ放送内での繰り返しには、`sam.build(...)`の第4引数を使えます。

このデータ取得例は通常放送用です。発車用JSに渡される`tile`には`receivedData`がありません。

## 発車用JSとの使い分け

| 項目 | 通常放送 | 発車放送 |
| --- | --- | --- |
| 配置先 | `assets/stationannouncemod/scripts/` | 同左 |
| 設定する装置 | 放送装置ブロック | 発車メロディブロック |
| 開始関数 | `samMain(tile)` | 同左 |
| `sam.build(...)` | `sam.build(startmelo, sounds, arrmelo[, repeatCount])` | `sam.build(melody, sounds, mode)` |
| 第1引数 | 冒頭メロディまたは`null` | 発車メロディの音声ID |
| 第2引数 | 本放送の音声パーツ | 戸閉放送の音声パーツと無音区間 |
| 第3引数 | 接近メロディまたは`null` | `sam.push()`または`sam.toggle()` |
| パーツ間の無音 | `sam.interval(秒数)` | `sam.interval(秒数)` |

発車用の具体例と再生モードは、[発車メロディの設定](howtoDepartureMelody.md)にまとめています。

# 発車メロディの設定

発車放送・スイッチ設定ガイドです。メロディと戸閉放送の内容はJS、スイッチの外観やクリック音はモデルJSONで指定します。

導入と基本の設置手順は[README](README.md)、音声ファイルとJSONの登録は[放送パックの作成](howtoAddAnnounce.md)を参照してください。

## 発車用JSを作る

### ON中にループする例

次の内容を`departure.js`として、パックの`assets/stationannouncemod/scripts/`に入れます。発車メロディブロックの設定画面には`departure.js`と入力します。

```javascript
function getDisplayName() {
    return "1番線・発車メロディ";
}

function samMain(tile) {
    var melody = "sound_sample:melody";

    var sounds = [];
    sounds.push("sound_sample:platform_1");
    sounds.push(sam.interval(0.25));
    sounds.push("sound_sample:door_close");
    sounds.push("sound_sample:please_stand_clear");

    var mode = sam.toggle().interval(0.5);
    return sam.build(melody, sounds, mode);
}
```

この例ではON中にメロディをループし、OFFにするとメロディを止めます。0.5秒後に番線案内を始め、0.25秒の間を挟んで戸閉案内を順番に再生します。

`sound_sample:*`は例です。使用するパックの音声IDへ置き換えてください。

### sam.build(melody, sounds, mode)

| 引数 | 内容 |
| --- | --- |
| `melody` | 発車メロディの音声ID（必須） |
| `sounds` | 戸閉放送のパーツ配列。音声IDと`sam.interval(...)`を追加した順に再生 |
| `mode` | `sam.push()`または`sam.toggle()`で作る再生モード |

`sounds`は音源と無音区間を合わせて最大256パーツです。戸閉放送を付けない場合は`[]`を渡します。

```javascript
return sam.build("sound_sample:melody", [], sam.toggle());
```

戸閉放送は`sounds.push(...)`で複数のパーツを組み合わせられます。`if`などで条件に応じたパーツを追加することもできます。

### 1回だけ再生する

再生モードを`sam.push()`にすると、操作1回で「メロディ1回 → 待ち時間 → 戸閉放送」を再生します。

```javascript
var mode = sam.push().interval(0.5);
return sam.build(melody, sounds, mode);
```

メロディから戸閉放送の終了まで、再操作による重複再生はしません。ON/OFF型スイッチを途中でOFFにしても、一連の放送は最後まで続きます。

### OFF後もコーラス末尾まで流す（立川式）

```javascript
var mode = sam.toggle().interval(0.5).tachikawa(true);
return sam.build(melody, sounds, mode);
```

OFFにしても現在のコーラス末尾までメロディを流します。戸閉放送は**OFFから0.5秒後**に始まるため、残りのメロディと重なって流れる場合があります。

`.tachikawa(true)`を省略するか`false`にすると、OFF時にメロディを即停止します。

### 待ち時間とパーツ間隔

| 記述 | 待ち時間の位置 | 指定範囲 |
| --- | --- | --- |
| `sam.push().interval(秒)` | メロディ終了から戸閉放送の開始まで | 0～3600秒。省略時0秒 |
| `sam.toggle().interval(秒)` | 最後のOFFから戸閉放送の開始まで | 0～3600秒。省略時0秒 |
| `sounds.push(sam.interval(秒))` | 戸閉放送配列のその位置 | 0.01～3600秒 |

待ち時間は小数第2位までを使用し、それより下を切り捨てます。実際の再生タイミングは通常0.05秒単位に切り上げられます。例えば`0.129`秒の指定は`0.12`秒として扱い、実際の待機は通常0.15秒になります。

### 音声の長さを登録する

メロディと戸閉放送の各音源を`sounds.json`に登録し、`sam_length.json`にそれぞれの長さを書きます。

```json
{
  "sound_sample:melody": { "length": 12.8 },
  "sound_sample:platform_1": { "length": 1.0 },
  "sound_sample:door_close": { "length": 4.2 },
  "sound_sample:please_stand_clear": { "length": 2.0 }
}
```

数値は実際の音源の長さに合わせてください。ループ用メロディは1コーラスを1ファイルにします。長さの未登録や不正な値があると再生できず、発車メロディ装置の設定画面などに対象の音声IDを含むエラーが表示されます。

## スイッチの使い方

### モデルと再生モードの組み合わせ

モデルの`switchMode`はボタンの戻り方を、JSの`sam.push()`／`sam.toggle()`は音声の再生方法を決めます。

| モデルの動作 | JSのモード | 操作と再生 |
| --- | --- | --- |
| ON/OFF型（`alternate`） | `sam.toggle()` | 右クリックごとにON／OFF。ON中はメロディをループ |
| 単押し型（`momentary`） | `sam.toggle()` | 押すたびに再生のON／OFFを切り替え。ボタンの見た目は押すたびに戻る |
| ON/OFF型（`alternate`） | `sam.push()` | ONにしたときに1回再生。再び使うときはOFFに戻してからONにする |
| 単押し型（`momentary`） | `sam.push()` | 押すと1回再生。放送終了後に押すと次の再生を開始 |

内蔵モデルは`melodysw_momentary_sample`（単押し型）と`melodysw_alternate_sample`（ON/OFF型）です。

### 複数のスイッチを使う

同じリンクキーのスイッチを複数設置できます。`sam.toggle()`では、それぞれのスイッチが独立したON／OFFを持ちます。

- 最初のスイッチをONにすると再生を開始します。
- 別のスイッチをONにしても、メロディはそのまま続きます。
- ONのスイッチが1つでも残っている間はループします。
- 最後のスイッチをOFFにすると、JSで指定した停止・戸閉放送へ進みます。
- OFF後の待ち時間や戸閉放送中に再びONにすると、その放送を打ち切ってメロディの先頭から再開します。

単押し型と`sam.toggle()`を組み合わせた場合も、各スイッチを押すたびにON／OFFが切り替わります。見た目が戻った後もONが続くので、終了時はONにしたスイッチをもう一度操作してください。

`sam.push()`では、どのスイッチから操作しても、放送中の追加操作で音声を重ねたり次の再生を予約したりはしません。

### レッドストーンで操作する

発車メロディブロックにレッドストーン信号を入力できます。

- `sam.push()`：OFFからONになったときに1回再生します。
- `sam.toggle()`：信号がONの間、メロディをループします。スイッチも併用している場合は、レッドストーンと全スイッチがOFFになったときに終了処理へ進みます。

### 向きと位置を調整する

設置時はプレイヤーの向きに合わせ、通常は15度、スニーク中は1度刻みで向きが決まります。

設置後はスニーク＋右クリックで設定画面を開き、次の項目を調整できます。「完了」で保存します。

| 設定 | 内容 |
| --- | --- |
| 角度 | 1度刻みの整数。0度＝南、90度＝東、180度＝北、270度＝西。360度は0度、-1度は359度として保存 |
| オフセット X / Y / Z | 表示位置を各座標軸の方向へ移動。小数で指定し、1.0が1ブロック |

オフセットはワールドの座標軸に沿って適用されます。モデルを回転させても移動方向は変わりません。見た目を移動しても、クリックする場所・当たり判定・クリック音の発生位置は設置したブロックに残ります。

### 設定をコピーする

クリエイティブモードで**左Shift＋マウス中央クリック**すると、モデル・リンクキー・オフセットなどを保持したアイテムを取得できます。再設置時の角度は、そのときのプレイヤーの向きで決まります。

### 停止とほかの放送との関係

発車メロディと戸閉放送は、親の放送装置と同じスピーカー・可聴範囲・音量を使います。同じ系統の通常放送より優先され、待ち時間中もその扱いが続きます。啓発放送は「ほかの放送と重ねて再生」の設定に従います。

放送停止ブロックや`/sam stopall`は、メロディと戸閉放送をまとめて停止し、関連スイッチもリセットします。発車メロディ装置の設定変更・撤去でも停止します。

ONのスイッチを撤去・リンク変更したり、そのチャンクが読み込まれなくなったりすると、そのスイッチのONが解除されます。最後のONがなくなった場合は、通常のOFFと同じ終了処理に進みます。ワールドを読み直した後は、改めて操作して再生を開始してください。

啓発放送の「発車メロディ終了後にも再生」は、メロディと戸閉放送の両方が通常終了した後に動作します。停止ブロックなどによる途中停止では、この発車後の放送は開始されません。

## 独自のスイッチモデルを追加する

### ファイルの配置

放送パックにモデルJSON、MQO、テクスチャを追加します。JSONとMQOは`assets/stationannouncemod/switches/`に置きます。サブフォルダも使用できます。

```text
SamplePack.zip
  assets/stationannouncemod/scripts/departure.js
  assets/stationannouncemod/switches/sample_switch.json
  assets/stationannouncemod/switches/sample_switch.mqo
  assets/stationannouncemod/switches/sample_switch.png
  assets/stationannouncemod/switches/button.png
  assets/sound_sample/sounds.json
  assets/sound_sample/sam_length.json
  assets/sound_sample/sounds/melody.ogg
  assets/sound_sample/sounds/platform_1.ogg
  assets/sound_sample/sounds/door_close.ogg
  assets/sound_sample/sounds/please_stand_clear.ogg
  assets/sound_sample/sounds/on.ogg
  assets/sound_sample/sounds/off.ogg
```

### モデルJSONの例

```json
{
  "name": "sample_departure_switch",
  "displayName": "サンプル発車メロディスイッチ",
  "tags": "ホーム 発車",
  "switchMode": "alternate",
  "model": {
    "modelFile": "sample_switch.mqo",
    "scale": 0.01,
    "offset": [0, 0, 0],
    "textures": [
      ["mat1", "sample_switch.png"]
    ]
  },
  "buttonTexture": "button.png",
  "sounds": {
    "on": "sound_sample:on",
    "off": "sound_sample:off"
  },
  "pressedState": {
    "translations": [
      {
        "parts": ["On"],
        "offset": [0, 0, -1.1]
      }
    ]
  }
}
```

この例では、ON時にMQOの`On`というパーツをZ方向へ移動します。`mat1`と`On`は、使用するMQOの材質名・Object名に合わせて変更してください。

| 項目 | 内容 |
| --- | --- |
| `name` | モデルを識別するID。パック間で重複しない名前。半角英数字と`_`、`.`、`:`、`-`を使用でき、最大128文字 |
| `displayName` | モデル一覧での表示名。省略時は`name` |
| `tags` | 検索用の文字列 |
| `switchMode` | `alternate`でON/OFF保持、`momentary`で単押し。省略時は`momentary` |
| `model.modelFile` | MQOファイルのパス |
| `model.scale` | モデル倍率。既定値0.01 |
| `model.offset` | モデル全体の位置補正`[X, Y, Z]`。倍率を掛ける前のモデル単位 |
| `model.textures` | `["材質名", "画像パス"]`の配列。`default`を材質名にすると、個別指定のない材質に適用 |
| `buttonTexture` | モデル選択一覧の名前に添える画像。省略可 |
| `sounds.on`／`sounds.off` | 操作時のクリック音の音声ID。省略または空文字で無音 |
| `pressedState` | 押下・ON状態のパーツ移動や表示切り替え |

MQO・テクスチャ・`buttonTexture`の相対パスは、JSONが置かれたフォルダを基準にします。`stationannouncemod:switches/sample_switch.png`のようなリソースIDでも指定できます。

### モデルの大きさ・原点

RTMと同じくcm単位で作成したMQOは、`scale: 0.01`でブロックの大きさに換算されます。描画原点は設置ブロックの底面中央、モデル正面の基準はZ負方向です。原点を補正したいときは`model.offset`を使います。

JSON内のパーツ移動量も倍率を掛ける前のモデル単位です。例の`offset: [0, 0, -1.1]`は、`scale: 0.01`ならZ方向へ0.011ブロック移動します。設置後の設定画面で入力するオフセットは、ブロック単位です。

v0.2.1の選択・当たり判定は、設置位置の1ブロック全体です。

### 押下状態を別パーツで表現する

通常時と押下時の形状をそれぞれMQOに用意し、表示を切り替えることもできます。モデルJSONの`pressedState`を次のように指定します。

```json
{
  "normalParts": ["ButtonNormal"],
  "pressedParts": ["ButtonPressed"]
}
```

通常時は`ButtonNormal`、押下・ON時は`ButtonPressed`を表示します。どちらにも指定しない本体パーツは常に表示されます。`translations`による移動と表示切り替えは併用できます。

### クリック音

`sounds.on`／`sounds.off`のIDを、パックの`sounds.json`に登録します。

```json
{
  "on": {
    "category": "master",
    "sounds": ["sound_sample:on"]
  },
  "off": {
    "category": "master",
    "sounds": ["sound_sample:off"]
  }
}
```

既存の`sounds.json`に追加する場合は、その中へ`on`と`off`の項目を追加してください。

ON/OFF型は切り替え時にそれぞれの音を、単押し型は押したときに`on`の音を鳴らします。単押し型のボタンは通常約0.1秒で戻り、戻るときは無音です。クリック音は操作したスイッチの位置から鳴ります。

### MQO作成時の注意

三角形・四角形、材質色、UV、Object名に対応しています。ミラーや曲面分割は書き出す前にフリーズし、それ以外の多角形は三角形または四角形に変換してください。

RTMモデルを移植する場合は、描画JSで行っていたパーツの移動・表示切り替えを`pressedState`へ書き換えます。内蔵モデルのJSONは、ModのJAR内の`assets/stationannouncemod/switches/`で確認できます。

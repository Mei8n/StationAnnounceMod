# 発車メロディとスイッチモデル

放送装置ブロックのスピーカー設定を共用する発車メロディブロックと、独立して複数配置できるスイッチです。メロディ・戸閉放送・再生モードは装置のJS、スイッチ固有の動作モード・外観・押下表現・クリック音はモデルJSONで指定します。

## 設置・モデル選択

1. 放送装置ブロック・スピーカー・発車メロディブロックに、固有のリンクキーを設定します。
2. メロディ装置のGUIに発車メロディ用JSのファイル名を入力します。
3. 発車メロディスイッチを手に持ち、空に向かって右クリックして、設置するモデルを選択します。選択内容は手に持っているアイテムへ保存されます。
4. スイッチを必要な場所に配置し、同じリンクキーを設定します。
5. 設置後もスイッチのGUIでモデルを変更できます。通常右クリックで操作、何も持っていない状態で左Shift＋右クリックで設定を開きます。リンクキー未設定時の通常右クリックは何も起こりません。

GUIはモデル名・タグによる検索、ホイールでの一覧スクロール、通常／押下プレビューに対応します。一覧はテキストが基本で、`buttonTexture` を指定したモデルだけ名前の後ろに画像を表示します。プレビューを切り替えても実際のスイッチ状態は変化しません。

アイテムの手持ち・インベントリ表示は選択モデルと連動せず、常に固定の平面アイコンです。

設置時の向きはプレイヤーの向きを通常15度、スニーク中は1度刻みの最寄り角度に丸めます。GUIの「角度」欄では整数を入力し、「完了」で保存します。360→0、-1→359、450→90のように正規化されます。空欄・小数・不正な文字列の場合は現在値へ戻り、GUIは開いたままになります。「キャンセル」やEscでは保存しません。

設置後のGUIでは、RTMの照明モデル等と同じ単位系の `X / Y / Z` オフセットを指定できます。値はfloat型のブロック単位（`1.0 = 1ブロック`）で、モデル倍率の影響を受けず、モデルの回転より前にワールド座標軸へ適用されます。表示位置・描画範囲へ反映されますが、操作・衝突判定とクリック音はオフセット先へ移動せず、常に設置位置の1ブロック全体に残ります。RTMと同様に有限値への範囲制限はありません。NaN・無限値などの不正値を入力した場合は現在値へ戻り、GUIは開いたままになります。

RTMモデル正面の基準はZ負方向で、Yawは0度が南、90度が東、180度が北、270度が西です（プレイヤーYawとは回転の符号が逆です）。標準モデルは水平面で対称です。角度はTileEntityの `RotationYaw`（float）だけに保存し、旧4方向形式は引き継ぎません。

モデルJSONの `switchMode` がスイッチ固有の動作を決めます。`alternate` は右クリックごとにON／OFFを切り替えて表示を保持し、`momentary` は押下後2tickで無音で戻ります。JSの再生モードを変えても、この機構は変わりません。モデルが未導入の場合は代替形状・単押し動作を使用し、GUIで不足を表示します。JSON・MQOの読み込みエラーはログにも出力します。

| スイッチ | JS | 動作 |
| --- | --- | --- |
| ON/OFF | `sam.toggle()` | 右クリックごとに再生制御もON／OFF |
| ON/OFF | `sam.push()` | ONへの切替で一度再生。OFFは再生に干渉しない。終了後もON表示を保持し、自動再実行・ループはしない |
| 単押し | `sam.toggle()` | 押すたびに再生制御のON／OFFを切替。表示の自動復帰では再生制御をOFFにしない |
| 単押し | `sam.push()` | 一度再生。動作中の連打は押下表示とクリック音のみ。終了後に押すと再実行 |

`sam.push()` の再生中は、どちらのスイッチからON・押下しても再開・重複再生・予約を行いません。「動作中」はメロディ・開始前インターバル・戸閉放送の全期間を含みます。

### メタデータ付きスポイト

スピーカーと同じ **左Shift＋マウス中央クリック** で、選択モデル・リンクキー・向き・オフセットを内包したアイテムを取得できます。クリエイティブモードで使用してください。

コピーしたアイテムを配置すると設定を復元しますが、角度は設置時のプレイヤーの向きから決定し直します。元の座標や一時的なON／押下状態は複製しません。また、通常のスポイトでは設定をコピーしません。

## 複数スイッチの動作

JSで `sam.toggle()` を指定した場合は、各スイッチの再生制御用ON／OFFを独立して保持します。単押しスイッチの押下表示が戻っても、この論理ONは保持します。

- 最初の1台をONにするとメロディをループ再生します。
- 別のスイッチを追加でONにしても、再生位置はリセットしません。
- ONのスイッチが残っている間は、ほかのスイッチをOFFにしても再生を続けます。
- 最後の1台をOFFにすると終了処理へ進みます。通常方式はメロディを即停止します。立川式は現在のコーラス末尾までメロディを続けます。どちらもOFFから開始前インターバル経過後に戸閉放送を開始し、立川式ではメロディと重なって流れることがあります。開始前インターバルが0秒ならOFFと同時に戸閉放送が始まります。
- 終了待ち・戸閉放送中に再ONすると、進行中のシーケンスを破棄し、メロディの先頭から再開します。

JSで `sam.push()` を指定した場合は、メロディ・開始前インターバル・戸閉放送を一度だけ順番に再生します。再生中の再操作でもスイッチ固有の表示・クリック音は動作しますが、音声シーケンスは重複再生しません。ON/OFFスイッチをOFFにしても、進行中の一連の再生は続きます。

クリック音は操作したスイッチの位置から鳴ります。`switchMode: momentary` は `sounds.on` だけを使い、自動復帰は無音です。`switchMode: alternate` は各スイッチの切替時に `sounds.on`／`sounds.off` を使います。ほかのスイッチや装置の集約状態が変化しただけではクリック音は鳴りません。

## パックの構造

放送パックと同じ `mods/SAMpacks/*.zip` に入れ、サーバーと各クライアントに同じパックを導入して再起動します。

```text
Example.zip
  assets/stationannouncemod/switches/example.json
  assets/stationannouncemod/switches/example.mqo
  assets/stationannouncemod/switches/example.png
  assets/stationannouncemod/switches/button.png
  assets/stationannouncemod/scripts/my_departure.js
  assets/example/sounds.json
  assets/example/sam_length.json
  assets/example/sounds/melody.ogg
  assets/example/sounds/door_close.ogg
  assets/example/sounds/on.ogg
  assets/example/sounds/off.ogg
```

MQOとモデルJSONを `assets/stationannouncemod/switches/` に配置します。サブフォルダも使用できます。通常放送用・発車メロディ用のJSは共に `assets/stationannouncemod/scripts/` に配置し、GUIではフォルダを含めず `.js` 付きのファイル名を入力します。用途ごとに異なるファイル名を使ってください。

パック間ではZIPファイル名順に読み込み、後から読み込んだ同名JSが優先されます。

### モデルJSON

モデル名 `name` はパック間で重複しないIDにします。

```json
{
  "name": "example_switch",
  "switchMode": "alternate",
  "displayName": "発車メロディスイッチ",
  "tags": "ホーム 発車",
  "model": {
    "modelFile": "example.mqo",
    "scale": 0.01,
    "offset": [0, 0, 0],
    "textures": [
      ["mat1", "example.png"]
    ]
  },
  "buttonTexture": "button.png",
  "sounds": {
    "on": "example:on",
    "off": "example:off"
  },
  "bounds": [0.3, 0, 0.3, 0.7, 0.4, 0.7],
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

- `modelFile`・テクスチャ・`buttonTexture` の相対パスは定義JSONのあるフォルダを基準に解決します。`stationannouncemod:switches/example.png` などの完全なリソースIDも指定できます。
- `switchMode` は `alternate`（ON/OFF保持）または `momentary`（単押し）。省略時は `momentary` です。それ以外の値は読み込みエラーになります。既存の外部モデルでON/OFF保持が必要な場合は明示してください。
- `model.textures` はMQOの材質名と画像の対応です。MQO内の古い絶対パスよりこちらを優先します。`default` を指定すると、明示的な対応のない材質に適用します。
- MQO座標はRTMと同じくcm単位を標準とし、`scale: 0.01` でブロック単位へ変換します。パーツ移動と `model.offset` は変換前のモデル単位です。
- 描画原点はブロックの底面中央です。`model.offset` はモデル全体の位置補正で、MQO自体を加工せずに原点を合わせるために使います。
- `bounds` は選択・当たり判定の範囲で、ブロック左下隅を原点に `[minX,minY,minZ,maxX,maxY,maxZ]` を指定します。0〜1の範囲で、向きに合わせて回転します。
- `buttonTexture` は省略可能です。省略時はテキストだけを表示します。
- `sounds.on`／`sounds.off` は音イベントIDです。`sounds.json` でOGGを登録します。省略／空文字で無音になります。
- テキストMQOの三角形・四角形、材質色、UV、Object名に対応します。ミラーや曲面分割はエクスポート前にフリーズし、多角形は三角形／四角形へ変換してください。RTM描画JSは実行しません。

### 押下状態を別パーツで表現する場合

移動量指定の代わりに、通常と押下のパーツをMQO内に用意して表示を切り替えられます。

```json
{
  "pressedState": {
    "normalParts": ["ButtonNormal"],
    "pressedParts": ["ButtonPressed"]
  }
}
```

通常時は `pressedParts` を隠し、押下時は `normalParts` を隠します。どちらにも指定しない本体パーツは常に表示します。表示切替とパーツ移動は併用可能です。いずれも補間せず、同期された状態へ即時切替します。

## メロディ装置のJS

```javascript
function getDisplayName() { return "1番線・立川式"; }

function samMain(tile) {
    var melody = "example:melody";

    var sounds = [];
    sounds.push("example:platform_1");
    sounds.push(sam.interval(0.25));
    sounds.push("example:door_close");
    sounds.push("example:please_stand_clear");

    var mode = sam.toggle().interval(0.5).tachikawa(true);
    return sam.build(melody, sounds, mode);
}
```

接近放送と同じ `getDisplayName()`・`samMain(tile)`・`sam.build(...)` の形で定義します。発車用 `sam.build(melody, sounds, mode)` の第1引数はメロディ音源ID、第2引数は戸閉放送のパーツ配列、第3引数は再生モードです。通常放送用の4引数形式 `sam.build(startmelo, sounds, arrmelo, repeatCount)` は発車メロディでは使用しません。`sounds.push(...)` で追加した順に各パーツを再生します。音源の間に `sounds.push(sam.interval(秒数))` を入れると、その位置へ無音区間を追加できます。条件分岐で追加するパーツを変更でき、空配列 `[]` または `null` なら戸閉放送なしになります。音源と無音区間を合わせて最大256パーツで、各音源の長さを `sam_length.json` に登録してください。

発車メロディ用JSの開始関数は `samMain(tile)` だけです。`configureDeparture()`、`onDepartureClick()`、`sam.alternate()`、`sam.momentary()`、`sam.buildDeparture()`、`.melody()`、`.doorClose()` は使用できません。

`samMain` は再生開始前にサーバーで実行されます。引数の `tile` は発車メロディ装置（`TileEntityDepartureMelody`）です。接近放送の `TileEntityAnnouncer` とは異なります。

| API | 動作 |
| --- | --- |
| `sam.push()` | 単押し。メロディを1回、開始前インターバル、戸閉放送 |
| `sam.toggle()` | ON中はループ。最後のスイッチがOFFになったら終了 |
| `sam.build(melody, sounds, mode)` | メロディ、戸閉放送のパーツ配列、再生モードから発車放送データを生成 |
| `sam.interval(秒)` | `sounds` 内の位置へ無音区間を追加。0.01〜3600秒 |
| `.tachikawa(true)` | 最後のOFF後も現在のコーラス末尾まで継続。戸閉放送はOFFから開始前インターバル後に並行再生 |
| モードの `.interval(秒)` | `sam.toggle()` は最後のOFFから、`sam.push()` はメロディ終了から戸閉放送までの開始前インターバル。既定0秒 |

クリック音はJSでは指定せず、モデルJSONの `sounds.on`／`sounds.off` で指定します。

モードへ指定する開始前インターバルは、小数第3位以下を切り捨てます（`0.129` → `0.12`秒）。JSファイル自体は書き換えません。実際の待機は20tick/秒で切り上げるため、`0.12`秒は3tick（通常20TPSで0.15秒）になります。`0.009`秒は切り捨て後0秒です。指定できる範囲は0〜3600秒で、負数・非有限値・上限超過はエラーです。

パーツ間の `sam.interval(秒)` も小数第2位までを使い、20tick/秒で切り上げます。0秒や、小数第2位までの切り捨てで0秒になる値は指定できません。無音区間は音源ではないため、`sounds.json` と `sam_length.json` への登録は不要です。

メロディ・戸閉放送の長さは必ずパック内の `sam_length.json` に指定します。メロディは `sam.build(...)` の第1引数へ、戸閉放送は第2引数の配列へ音源IDを指定します。開始前インターバルは再生モードの `.interval(秒)`、パーツ間隔は配列内の `sam.interval(秒)` で指定します。

```json
{
  "example:melody": { "length": 12.8 },
  "example:platform_1": { "length": 1.0 },
  "example:door_close": { "length": 4.2 },
  "example:please_stand_clear": { "length": 2.0 }
}
```

20tick/秒で切り上げ、指定時間をコーラス終了判定に使います。ループ用音源は1コーラス1ファイルとし、録音の長さを正しく指定してください。長さが不明な場合は再生せず、装置GUI・プレイヤーメッセージ・ログにエラーを表示します。

## 停止・保存・サンプル

メロディ・戸閉放送は放送装置ブロックのスピーカー位置・可聴範囲・音量を引き継ぎます。通常放送より高い優先度で、開始前・パーツ間の各インターバル中も優先度を保持します。メロディと戸閉放送の両方が終了した時点で1回だけ完了通知し、連携する啓発放送の発車後タイマーを起動します。

スイッチの撤去・リンク変更・アンロードは、そのスイッチのONを解除します。残りがONなら継続し、最後のONがなくなれば通常のOFF処理へ進みます。放送停止ブロック・`/sam stopall`・メロディ装置の設定変更や撤去・親装置の消失は、シーケンスと関連スイッチの状態を取り消します。設定は保存しますが、ワールド再読み込みで古いONを復元・自動再生しません。

メロディ装置へのレッドストーン入力は引き続き使用できます。`sam.push()` は立上りで1回、`sam.toggle()` は追加のON入力として扱います。レッドストーンとリンクスイッチの両方がOFFになってから終了します。レッドストーン入力ではスイッチのクリック音は鳴りません。

上記のJS例を参考に、音源IDの `example:*` をパックで登録したIDへ置き換え、パックZIPの `assets/stationannouncemod/scripts/` に入れてください。単押しにする場合は `sam.toggle()` を `sam.push()` へ変更します。装置GUIには配置したJSのファイル名を指定します。内蔵モデルは単押し型の `melodysw_momentary_sample` とON/OFF型の `melodysw_alternate_sample` です。

内蔵モデルのJSON定義は `assets/stationannouncemod/switches/` に同梱されています。外部パックで独自のスイッチを追加する場合も、JSONと必要なMQO・テクスチャをパックZIPの同じパスに配置してください。

`switch_sample` にある過去のRTMパックはモデル移植時の参考用です。元ファイルの変更・再配置・JAR同梱は行っていません。RTM描画JSにある `On` パーツのZ方向−0.011m移動は、上記JSONの `offset: [0,0,-1.1]` と対応します。RTMポイントそのものを発車スイッチ化する連携は使用せず、SAMの独立スイッチとして扱います。

サーバーとクライアントのMod・パックは同じ版に揃えてください。

## 検証コマンド

`gradlew verifyDeparture verifySwitchModels` で再生時間、JS、通信、MQO・JSON、独立スイッチ操作、設定付きコピーを検証します。`gradlew build` にも含まれます。検証用ワールドはメモリ上で動作し、実ワールドは変更しません。

ゲーム内ではモデル選択・通常／押下表示・実音と、複数クライアントの同期を確認してください。

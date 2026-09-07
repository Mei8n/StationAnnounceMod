# 発車メロディとスイッチモデル

放送装置ブロックのスピーカー設定を共用する発車メロディブロックと、独立して複数配置できるスイッチです。メロディ・戸閉放送・再生モードは装置のJS、スイッチ固有の動作モード・外観・押下表現・クリック音はモデルJSONで指定します。

## 設置・モデル選択

1. 放送装置ブロック・スピーカー・発車メロディブロックに、固有のリンクキーを設定します。
2. メロディ装置のGUIに発車メロディ用JSのファイル名を入力します。
3. 発車メロディスイッチを必要な場所に配置し、同じリンクキーを設定します。
4. スイッチのGUIでモデルを選択して保存します。通常右クリックで操作、何も持っていない状態で左Shift＋右クリックで設定を開きます。リンクキー未設定時の通常右クリックは何も起こりません。

GUIはモデル名・タグによる検索、ホイールでの一覧スクロール、通常／押下プレビューに対応します。一覧はテキストが基本で、`buttonTexture` を指定したモデルだけ名前の後ろに画像を表示します。プレビューを切り替えても実際のスイッチ状態は変化しません。

設置時の向きはプレイヤーの向きを通常15度、スニーク中は1度刻みの最寄り角度に丸めます。GUIの「角度」欄では整数を入力し、「完了」で保存します。360→0、-1→359、450→90のように正規化されます。空欄・小数・不正な文字列の場合は現在値へ戻り、GUIは開いたままになります。「キャンセル」やEscでは保存しません。

モデル正面の基準はZ正方向で、Yawは0度が南、90度が東、180度が北、270度が西です（プレイヤーYawとは回転の符号が逆です）。標準モデルは水平面で対称です。角度はTileEntityの `RotationYaw`（float）だけに保存し、旧4方向形式は引き継ぎません。

モデルJSONの `switchMode` がスイッチ固有の動作を決めます。`alternate` は右クリックごとにON／OFFを切り替えて表示を保持し、`momentary` は押下後2tickで無音で戻ります。JSの再生モードを変えても、この機構は変わりません。モデルが未導入の場合は代替形状・単押し動作を使用し、GUIで不足を表示します。JSON・MQOの読み込みエラーはログにも出力します。

| スイッチ | JS | 動作 |
| --- | --- | --- |
| ON/OFF | `sam.alternate()` | 右クリックごとに再生制御もON／OFF |
| ON/OFF | `sam.momentary()` | ONへの切替で一度再生。OFFは再生に干渉しない。終了後もON表示を保持し、自動再実行・ループはしない |
| 単押し | `sam.alternate()` | 押すたびに再生制御のON／OFFを切替。表示の自動復帰では再生制御をOFFにしない |
| 単押し | `sam.momentary()` | 一度再生。動作中の連打は押下表示とクリック音のみ。終了後に押すと再実行 |

上表は `onDepartureClick(click)` で `click.toggle()` を呼ぶ通常の操作です。単押しJSの再生中は、どちらのスイッチからON・押下しても再開・重複再生・予約を行いません。「動作中」はメロディ・インターバル・戸閉放送の全期間を含みます。

### メタデータ付きスポイト

スピーカーと同じ **左Shift＋マウス中央クリック** で、選択モデル・リンクキー・向きを内包したアイテムを取得できます。クリエイティブモードで使用してください。

コピーしたアイテムを配置すると設定を復元しますが、角度は設置時のプレイヤーの向きから決定し直します。元の座標や一時的なON／押下状態は複製しません。また、通常のスポイトでは設定をコピーしません。

## 複数スイッチの動作

JSがオルタネイトの場合は、各スイッチの再生制御用ON／OFFを独立して保持します。単押しスイッチの押下表示が戻っても、この論理ONは保持します。

- 最初の1台をONにするとメロディをループ再生します。
- 別のスイッチを追加でONにしても、再生位置はリセットしません。
- ONのスイッチが残っている間は、ほかのスイッチをOFFにしても再生を続けます。
- 最後の1台をOFFにすると終了処理へ進みます。通常方式はメロディを即停止します。立川式は現在のコーラス末尾までメロディを続けます。どちらもOFFからインターバル経過後に戸閉放送を開始し、立川式ではメロディと重なって流れることがあります。インターバル0秒ならOFFと同時に戸閉放送が始まります。
- 終了待ち・戸閉放送中に再ONすると、進行中のシーケンスを破棄し、メロディの先頭から再開します。

JSがモーメンタリの場合は、メロディ・インターバル・戸閉放送を一度だけ順番に再生します。再生中の再操作でもスイッチ固有の表示・クリック音は動作しますが、音声シーケンスは重複再生しません。ON/OFFスイッチをOFFにしても、進行中の一連の再生は続きます。

クリック音は操作したスイッチの位置から鳴ります。単押しは `sounds.on` だけを使い、自動復帰は無音です。オルタネイトは各スイッチの切替時に `sounds.on`／`sounds.off` を使います。ほかのスイッチや装置の集約状態が変化しただけではクリック音は鳴りません。

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

function configureDeparture(device) {
    return sam.alternate()
        .melody("example:melody")
        .doorClose("example:door_close")
        .interval(0.5)
        .tachikawa(true);
}

function onDepartureClick(click) {
    click.toggle();
}
```

| API | 動作 |
| --- | --- |
| `sam.momentary()` | 単押し。メロディを1回、インターバル、戸閉放送 |
| `sam.alternate()` | ON中はループ。最後のスイッチがOFFになったら終了 |
| `.tachikawa(true)` | 最後のOFF後も現在のコーラス末尾まで継続。戸閉放送はOFFからインターバル後に並行再生 |
| `.melody(id)` | メロディの音源ID。必須。長さは `sam_length.json` から取得 |
| `.doorClose(id)` | 戸閉放送の音源ID。省略／空文字でなし。長さは `sam_length.json` から取得 |
| `.interval(秒)` | オルタネイトは最後のOFFから、モーメンタリはメロディ終了から戸閉放送までの間隔。既定0秒 |
| `click.press()` | 単押しJSの一度再生を要求。再生中は無視 |
| `click.toggle()` | JSの再生モードに応じて一度再生、または対象スイッチの論理ON/OFF切替 |
| `click.on()` / `click.off()` | OnOff JSに対する対象スイッチの論理ON/OFFを指定 |
| `click.isOn()` | 操作対象スイッチの再生制御用の論理ON状態。押下表示や他スイッチの状態ではない |
| `click.isAlternate()` | JSの再生モードがオルタネイトかを取得 |

クリック音はJSでは指定しません。旧 `.clickOn()`／`.clickOff()` はモデルJSONの `sounds` へ移してください。

インターバルはJSの設定値を取り込む際に小数第3位以下を切り捨てます（`0.129` → `0.12`秒）。JSファイル自体は書き換えません。実際の待機は20tick/秒で切り上げるため、`0.12`秒は3tick（通常20TPSで0.15秒）になります。`0.009`秒は切り捨て後0秒です。指定できる範囲は0〜3600秒で、負数・非有限値・上限超過はエラーです。

`configureDeparture` は再生開始前、`onDepartureClick` はクリックごとにサーバーで実行されます。ただしON/OFFスイッチ＋単押しJSのOFF操作ではコールバックを呼びません。クリック処理が正常終了してから表示と再生要求を適用します。何も呼ばなければ再生制御は行いませんが、モデル固有の押下表示・クリック音は動作します。JSのグローバル変数は同じJSを使う装置間で共用されるため、論理ON状態は `click.isOn()` で取得してください。

メロディ・戸閉放送の長さは必ずパック内の `sam_length.json` に指定します。`.melody(id, 秒数)`・`.doorClose(id, 秒数)` は廃止しました。外部パックのJSも秒数引数を削除し、音源IDだけを渡してください。`.interval(秒)` は引き続きJSで指定します。

```json
{
  "example:melody": { "length": 12.8 },
  "example:door_close": { "length": 4.2 }
}
```

20tick/秒で切り上げ、指定時間をコーラス終了判定に使います。ループ用音源は1コーラス1ファイルとし、録音の長さを正しく指定してください。長さが不明な場合は再生せず、装置GUI・プレイヤーメッセージ・ログにエラーを表示します。

## 停止・保存・サンプル

メロディ・戸閉放送は放送装置ブロックのスピーカー位置・可聴範囲・音量を引き継ぎます。通常放送より高い優先度で、インターバル中も優先度を保持します。メロディと戸閉放送の両方が終了した時点で1回だけ完了通知し、連携する啓発放送の発車後タイマーを起動します。

スイッチの撤去・リンク変更・アンロードは、そのスイッチのONを解除します。残りがONなら継続し、最後のONがなくなれば通常のOFF処理へ進みます。放送停止ブロック・`/sam stopall`・メロディ装置の設定変更や撤去・親装置の消失は、シーケンスと関連スイッチの状態を取り消します。設定は保存しますが、ワールド再読み込みで古いONを復元・自動再生しません。

メロディ装置へのレッドストーン入力は引き続き使用できます。モーメンタリは立上りで1回、オルタネイトは追加のON入力として扱います。レッドストーンとリンクスイッチの両方がOFFになってから終了します。レッドストーン入力ではスイッチのクリック音は鳴りません。

サンプルJSは同梱していません。外部パックを導入し、装置GUIの空の入力欄に使用するJSファイル名を指定してください。内蔵モデル `sam_push` は移動量方式、`sam_alternate` はパーツ差分方式の例で、どちらもSAM用の簡易モデルです。

プロジェクトルートの `switch_alternate_sample.json`（ON/OFF）と `switch_momentary_sample.json`（単押し）は、パックZIPの `assets/stationannouncemod/switches/` に入れて使用できます。それぞれ固有のIDを持ち、同梱MQOを完全なリソースIDで参照するため、MQOの追加コピーは不要です。

`switch_sample` にある過去のRTMパックは参考・互換性検証用です。元ファイルの変更・再配置・JAR同梱は行っていません。旧JSの `On` パーツのZ方向−0.011m移動は、上記JSONの `offset: [0,0,-1.1]` と対応します。旧RTMポイントそのものを発車スイッチ化する連携は使用せず、将来の移植時もSAMの独立スイッチとして扱います。

既存の `soundId` だけを設定したメロディ装置は従来の単発再生として読み込み、新しいJS選択を優先します。この旧形式も `sam_length.json` の長さが必須で、未登録時は再生しません。既存SAMスイッチのモデル未指定データは `sam_push` として読み込みます。サーバーとクライアントのMod・パックは同じ版に揃えてください。

## 検証コマンド

`gradlew verifyDeparture verifySwitchModels` で再生時間、JS、通信、MQO・JSON、独立スイッチ操作、設定付きコピーを検証します。`gradlew build` にも含まれます。検証用ワールドはメモリ上で動作し、実ワールドは変更しません。

ゲーム内ではモデル選択・通常／押下表示・実音と、複数クライアントの同期を確認してください。

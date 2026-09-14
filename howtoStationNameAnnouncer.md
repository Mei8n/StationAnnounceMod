# 駅名連呼ブロック

駅名連呼は「大阪、大阪です」のような放送です。接近放送に付随する到着案内とは別の機能です。

## ブロック

- RS式駅名連呼ブロック: RSのOFF→ONで1回実行します。ON継続中は再実行せず、OFFに戻すと次の立ち上がりを受け付けます。
- RTM停車検知式駅名連呼ブロック: RealTrainMod連携が利用できる場合だけCreative Tabに表示されます。ブロック直上の `x..x+1 / y..y+3 / z..z+1` にある制御車が厳密に速度0になった時、停車イベントごとに1回実行します。

どちらもLink Keyで親の放送装置を指定し、駅名連呼用JSファイル名を設定します。親のSpeaker routing、通常Announcement session、late join同期をそのまま使用します。

## JavaScript

通常放送と同じ `sam.build(...)` を使用し、ScriptTypeだけを`STATION_NAME`（2）にします。型宣言のない従来形式（UNKNOWN）も使用できます。

```javascript
function getDisplayName() {
    return "大阪駅 駅名連呼";
}

function getScriptType() {
    return 2;
}

function samMain(tile) {
    return sam.build(
        null,
        [
            "sample:osaka_1",
            sam.interval(0.25),
            "sample:osaka_2"
        ],
        null,
        2
    );
}
```

`tile`はリンク先の親放送装置から作られたread-only `AnnounceScriptContext`です。実TileEntityや検出したRTM列車は渡されません。既知のAPPROACH、ARRIVAL、DEPARTURE_MELODY、AWARENESS型のJSはGUI候補・サーバー設定・実行時検証で拒否されます。

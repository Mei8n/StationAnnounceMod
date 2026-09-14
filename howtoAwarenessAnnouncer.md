# 啓発放送ブロック

啓発放送ブロックは、Link Keyで接続した親の放送装置とSpeakerを使い、設定間隔または発車メロディ終了後に放送します。再生priorityは常に啓発放送用で、JSから変更できません。

## DIRECTモード

音声IDをカンマ区切りで登録します。「ランダムな順番で再生」がOFFなら登録順、ONなら発火ごとにランダムで1件を再生します。

```text
sample:awareness_1, sample:awareness_2
```

## SCRIPTモード

1回の発火ごとにJSの`samMain(tile)`を1回実行し、返された`AnnounceData`全体を再生します。複数音声、無音区間、repeatを通常放送と同じ形式で指定できます。SCRIPTモードではDIRECT用のランダム設定を使用しません。

```javascript
function getDisplayName() {
    return "ホーム啓発放送";
}

function getScriptType() {
    return 4;
}

function samMain(tile) {
    return sam.build(
        null,
        [
            "sample:awareness_1",
            sam.interval(1.0),
            "sample:awareness_2"
        ],
        null
    );
}
```

使用できるScriptTypeは`AWARENESS`（4）と、型宣言のない`UNKNOWN`だけです。`tile`はリンク先の親放送装置から作られたread-only `AnnounceScriptContext`で、`tile.linkKey`と`tile.receivedData`を参照できます。

未設定、存在しないJS、実行例外、不正な戻り値ではその発火だけが不発になります。次の設定間隔までは再試行しないため、毎tick実行やログの連続出力は発生しません。

## 共通設定

| 設定 | 動作 |
| --- | --- |
| 放送間隔 | 周期発火の間隔。DIRECT/SCRIPT共通 |
| ほかの放送と重ねて再生 | ONなら高priority放送中も重ねて再生。OFFなら啓発放送のtimelineを一時停止 |
| 発車メロディ終了後にも再生 | 発車メロディと戸閉放送が正常終了した後に1回発火 |
| 発車後間隔 | 発車完了から啓発放送までの待機時間 |
| RS入力時のみ動作 | poweredの間だけ新しい発火を許可 |

「RS入力時のみ動作」のRSは開始triggerではなくEnable条件です。RSをONにした瞬間には再生せず、周期timerの残りから再開します。RS OFF中はtimer、JS、音声選択、ランダム選択を進めません。すでに開始済みの放送は停止しません。

発車後の待機中にRSがOFFになると、その発車イベントに対応する予約を破棄します。後からONへ戻しても古いイベントは復活せず、次の発車完了から新しく予約します。

DIRECTとSCRIPTの設定値はモードを切り替えても互いに消去されません。旧worldでモードやRS設定がない場合はDIRECT・RS不要として読み込み、従来の動作を維持します。

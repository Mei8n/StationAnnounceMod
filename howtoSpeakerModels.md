# Speakerモデル

Speakerは従来どおりモデルなしで使用できます。設定画面または設置前のSpeakerアイテム画面で、SAM Pack内のMQOモデル、Yaw、ブロック単位のXYZオフセットを選択できます。モデル設定は見た目だけに影響し、Link Key、可聴範囲、音量、routingは変えません。

## ファイル配置

SpeakerモデルはPack内の次のrootへ配置します。

```text
assets/stationannouncemod/speakers/
  platform_speaker.json
  platform_speaker.mqo
  platform_speaker.png
```

同じ`name`を持つモデルを複数登録することはできません。Modには`sam_speaker1`がサンプルとして含まれています。

## JSON

```json
{
  "name": "platform_speaker",
  "displayName": "ホーム用スピーカー",
  "tags": "speaker platform station",
  "smoothing": true,
  "doCulling": true,
  "model": {
    "modelFile": "platform_speaker.mqo",
    "scale": 0.01,
    "offset": [0, 0, 0],
    "textures": [
      ["body", "platform_speaker.png"],
      ["default", "platform_speaker.png"]
    ]
  },
  "bounds": [0.25, 0.0, 0.25, 0.75, 1.0, 0.75]
}
```

| 項目 | 内容 |
| --- | --- |
| `name` | モデルID。半角英数字と`_ . : -`、最大128文字 |
| `displayName` | GUIに表示する名前。省略時は`name` |
| `tags` | GUI検索用文字列 |
| `smoothing` | `true`でMQO Objectの`facet`角に基づくRTM方式の法線補間 |
| `doCulling` | `true`でRTM方式の片面描画 |
| `model.modelFile` | JSON位置を基準にしたMQO path |
| `model.scale` | モデル倍率。cm単位のMQOでは通常`0.01` |
| `model.offset` | MQO単位でのモデル原点補正 |
| `model.textures` | MQO材質名と画像pathの組 |
| `bounds` | モデルのlocal描画範囲`[minX,minY,minZ,maxX,maxY,maxZ]` |

Metasequoiaでcm単位にした場合、`scale: 0.01`ではY=100がブロック上辺になります。描画原点はブロック底面中央です。JSONの`model.offset`はMQOモデル自体の補正、GUIのOffset X/Y/ZはSpeaker個体をブロック座標で移動する設定です。

## テクスチャ

MQO内の`tex("...")`は絶対path・相対pathとも使用しません。JSONの`model.textures`だけがSAMでの指定です。

```text
MQO材質名と完全一致するJSON指定
→ 指定がなければdefault
→ それもなければMinecraft標準missing texture
```

材質の個別指定が存在していて、そのpathや画像が壊れている場合は`default`へ切り替えずmissing textureを表示します。未使用のtexture entryや画像欠落だけでMQO geometry全体が無効になることはありません。MQO自体の読込・parseに失敗した場合はモデルを描画せず、同じresourceを毎frame読み直しません。resource reload時にcacheを破棄して再読込できます。

## GUIと設置

一覧の先頭にある「モデルなし」は内部的に空のモデル名です。新規Speakerとモデル情報を持たない旧Speakerはこの状態になり、通常時は従来どおり不可視です。Packから設定済みモデルが消えた場合も別モデルへ自動変更せず、音声機能を維持したままGUIに欠落を表示します。

モデル一覧は`name`、`displayName`、`tags`で検索でき、3D previewを確認できます。Yawは1度単位、Offset X/Y/Zはブロック単位で設定します。設置時のYawはプレイヤーの向きから決まり、通常設置では15度、スニーク設置では1度刻みです。コピーしたNBTにYawが含まれていても、新しい設置方向を優先します。

モデルが設定されていても、SpeakerアイテムまたはRTMの検査道具を持った時の枠・可聴範囲・beam表示は従来どおり使用できます。

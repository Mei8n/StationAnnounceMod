# SAM ネットワーク改修報告

対象: Minecraft 1.7.10 / Forge 10.13.4.1614 / Java 8。

## Speaker fallback同期判定の回帰修正

変更ファイルは `TileEntitySpeaker.java`、`AnnounceManager.java`、`NetworkVerificationTest.java`、本書の4ファイル。packet、discriminator、各件数上限、server session/TTL、queue、recipient探索には変更を加えていない。

`TileEntitySpeaker`はprivateな `clientConfigSynced` を持ち、constructorおよび通常の `readFromNBT` 後はfalseのままとする。Minecraft 1.7.10の一般TE用S35受信経路は `NetHandlerPlayClient.handleUpdateTileEntity` から対象TEの `onDataPacket` を呼ぶため、Speaker設定NBTをそこで適用し終えた後だけtrueにする。`AnnounceManager`は `isClientConfigSynced()` がtrueのvalid Speakerだけをauthoritativeとして扱う。

未同期SpeakerはTEインスタンスが存在しても未解決と同じfallback/retry経路へ進む。fallback受信後にS35が届いた場合、次の対象音評価では同期済みTEの現在値を優先して古いfallbackを削除する。同じlinkKeyなら現在のrange/volumeで再生し、別linkKeyまたは正式に同期された空linkKeyなら再生せずfallbackへ戻らない。authoritative判定後にTEがunloadされても削除済みfallbackは復活させない。

headless fixtureでも、配置しただけのdefault TEと、server側TEの `getDescriptionPacket()` をclient側 `onDataPacket()`へ適用したTEを区別した。未同期TEでのdefer/Missing/fallback、同期後の同一key、別key、空key、同期済みTE削除、終了後の遅延fallback、`readFromNBT`単独では同期済みにならないことを検証している。最終結果は `verifyNetwork` **162 checks**、`verifyDeparture` 190 checks、`verifySwitchModels` 177 checksで、`test check --offline`も成功した。実ゲームでの手動確認は未実施。

## 0.2.1-beta 上限・TTL追加修正（PacketFix）

この段階の変更ファイルは次の10ファイル。追加・削除ファイル、新packet、discriminator変更はない。後続の初回PacketFix改修一覧とは区別する。

- `src/main/java/jp/me1han/sam/StationAnnounceModCore.java`
- `src/main/java/jp/me1han/sam/client/AnnounceManager.java`
- `src/main/java/jp/me1han/sam/network/PacketLimits.java`
- `src/main/java/jp/me1han/sam/network/PacketAnnounce.java`
- `src/main/java/jp/me1han/sam/network/PacketMissingSpeakers.java`
- `src/main/java/jp/me1han/sam/network/PacketSpeakerFallback.java`
- `src/main/java/jp/me1han/sam/network/ServerSessions.java`
- `src/main/java/jp/me1han/sam/network/ServerTaskQueue.java`
- `src/test/java/jp/me1han/sam/network/NetworkVerificationTest.java`
- `NETWORK_REVIEW.md`

| 問題 | 最終仕様 |
| --- | --- |
| 旧版と同じMODバージョン | VERSION=`0.2.1-beta`。Forge標準 `acceptableRemoteVersions="[0.2.1-beta]"` により接続先の同バージョンだけを許可 |
| 過大なSpeaker配列 | START/fallbackは `SESSION_TARGETS=512`、Missingは同値の `MISSING_TARGETS`。decodeとencodeで検証 |
| 実際のSTARTより多い補完要求 | allowed取得直後、HashSet確保前にrequest件数≤allowed件数を検証。不正な一回目も要求権を消費し、二回目は無視 |
| bodySounds 4096件 | `BODY_SOUNDS=256`。serverでは超過sequenceのSTART全体を送信・session作成前にrejectし、警告を記録。途中で音声列を切り詰めない |
| ACKのdropによる永久leak | 24時間相当のserver tick TTLと200tick間隔の期限切れ掃除 |
| 古いfallbackが正式TEより優先 | serverのS35設定を適用済みのvalid Speaker TEだけをauthoritativeとしてfallbackを削除。TEが存在しても設定同期前ならfallback/retryを維持する。同期済みの空文字を含むlinkKey不一致は正式状態として再生せず、その音の再試行も行わない |

Speakerが一人の受信範囲に512台を超えて存在する場合は、Registry列挙順で先頭512台までをそのplayerのSTART対象にする（近い順の選別ではない）。512台に達したらそのplayer向け列挙を打ち切るため、巨大な中間配列を作らず、decoderの上限を超えるSTARTも送らない。station全体のSpeaker登録数にはこの上限を適用しない。

Missingのpayload上限は12+8×512=**4108bytes**、fallbackは12+16×512=**8204bytes**。STARTの座標部分は最大**4096bytes**（共通ヘッダーと音声列は別）。これらはForge/Minecraftフレームヘッダーを含まない。bodySoundsの256件制限を決める際は `run/mods/SAMpacks` の `sam_kasamashi.zip`, `sam_Reikyu_dev.zip`, `sam_SamplePack.zip` を読み取り、正常な通常放送scriptの列が最大6件であること、既存テストも256件以内であることを確認した。外部の全SAMpacksの互換性を保証する調査ではない。

TTLはSTART時点から **1,728,000 logical server ticks**。ServerTick ENDでカウンターだけを進め、**200ticks（20TPSで10秒）に一度**、session mapを検査する。通常のworld時刻変更やclient要求で期限は変わらない。server発行のDeparture RELEASE/OFF時は、その時点から同じ24時間分延長する。FINISHED正常受信・STOP/CANCEL等による即時解放は従来どおり。Missing要求では期限を延長しない。

期限切れでは対象session IDのSTOPを元recipientへ送り、session/recipient逆引き/補完認可記録を解放する。ACKが欠落しても期限から最大199ticks後の掃除で解放される。duration推定による短い期限ではなく、Awareness待機や長いループにも余裕を持たせた保険期限である。そのため**正常でも24時間無操作で続くsessionは停止する**。20TPS未満では実時間の24時間より長くなる。server停止/global stopは全sessionとTTLカウンターをclearし、session IDの単調増加は維持する。

queueは引き続き最大1024件、START tickあたり256件。超過分はFINISHEDを含めdropする。別priority queueや再送protocolは追加せず、queue外のServerTick ENDのTTL掃除を保険とした。GUIがqueueを埋めてもTTL処理はqueueに依存しない。

独立したprotocol番号や独自handshakeは追加していない。Forge標準handshakeが持つMODバージョン情報で厳密照合する。使用されないNETWORK_PROTOCOL_VERSION定数だけを追加することも避けた。

自動検証: `gradlew.bat test check --offline --console=plain` 成功。`verifyDeparture` 190、`verifySwitchModels` 177、`verifyNetwork` 162 checks成功。追加検証にはdecode上限、送信側512件制限、11対10要求、不正一回目の消費、FINISHED handler、期限境界と別session保護、OFFによる延長、queue overflowとTTL保険、logout/dimension/respawn/owner unload/world unload/server stop/global stop、未同期Speaker TEのfallback、同期済みTEのrange/volumeおよび不一致・空linkKey優先、終了後のfallback無視を含む。`git diff --check` も実行した。

今回はrecipient探索のspatial index化、packet ID 0〜16（7 retired）、新packet追加、priority queue化、Speaker/LoadedSamTiles index、GUIアクセス検証、DepartureSequenceのタイミングを変更していない。development clientとDedicated Serverを同時起動しての実聴・手動操作確認は未実施。headlessの既存実処理fixtureで検証した。

以下は初回PacketFix改修の全体記録で、数値・fallback・TTL・互換性は0.2.1-betaの仕様に更新済み。

通常の放送は、対象linkKeyのSpeakerとローカル音源の近くにいるplayerへ、session単位で配送する。Speakerは毎tickの登録を行わず、STARTには座標IDだけを含める。通常TE同期を優先し、TEが未解決の場合だけ小さな補完応答を使う。

## 参照した実装

- [KaizPatchX PacketPlaySound](https://github.com/Kai-Z-JP/KaizPatchX/blob/master/src/main/java/jp/ngt/rtm/network/PacketPlaySound.java): 音声イベントの情報にpayloadを限定し、対象Entity/TEを解決する構成。
- [PacketCustom](https://github.com/Kai-Z-JP/KaizPatchX/blob/master/src/main/java/jp/ngt/ngtlib/network/PacketCustom.java): TE座標から `world.getTileEntity()` で解決。
- [CommonProxy.playSound](https://github.com/Kai-Z-JP/KaizPatchX/blob/master/src/main/java/jp/ngt/rtm/CommonProxy.java): `RTMCore.NETWORK_WRAPPER.sendToAllAround()` と `NetworkRegistry.TargetPoint`。
- [TileEntityElectricalWiring](https://github.com/Kai-Z-JP/KaizPatchX/blob/master/src/main/java/jp/ngt/rtm/electric/TileEntityElectricalWiring.java): `validate` 登録、`invalidate` 削除、`onChunkUnload` 登録解除。

SAMでは複数Speakerの範囲重複を考慮して受信者を重複排除する。KaizPatchXの固定radius=256やNBT形式のtarget payloadはコピーしていない。

## 1. 変更ファイル

既存ファイルの変更一覧（パスの共通接頭辞は `src/main/java/jp/me1han/sam/`）:

| 場所 | ファイル |
| --- | --- |
| project root | `build.gradle` |
| sam | `CommandSAM.java`, `DepartureSwitchLink.java`, `SpeakerRegistry.java`, `StationAnnounceModCore.java` |
| client | `AnnounceManager.java` |
| gui | `GuiSpeaker.java` |
| network | `NetworkHandler.java`, `PacketAnnounce.java`, `PacketDepartureControl.java` |
| network / GUI config | `PacketConfig.java`, `PacketTrainTypeConfig.java`, `PacketDebugConfig.java`, `PacketStartAnnouncerConfig.java`, `PacketStopAnnouncerConfig.java`, `PacketSpeakerConfig.java`, `PacketAwarenessConfig.java`, `PacketDepartureMelodyConfig.java`, `PacketDepartureSwitchConfig.java` |
| render | `TileEntitySpeaker.java`, `TileEntityAnnouncer.java`, `TileEntityAwarenessAnnouncer.java`, `TileEntityDepartureMelody.java`, `TileEntityDepartureSwitch.java`, `TileEntityStartAnnouncer.java`, `TileEntityStopAnnouncer.java`, `TileEntityTrainTypeSelector.java`, `TileEntityDebugReceiver.java` |
| src/test/java/jp/me1han/sam | `DeparturePlaybackTest.java`, `SwitchModelTest.java` |

新規ファイル:

- `src/main/java/jp/me1han/sam/LoadedSamTiles.java`
- `src/main/java/jp/me1han/sam/render/RegisteredTileEntity.java`
- `src/main/java/jp/me1han/sam/network/ConfigAccess.java`
- `src/main/java/jp/me1han/sam/network/PacketLimits.java`
- `src/main/java/jp/me1han/sam/network/ServerTaskQueue.java`
- `src/main/java/jp/me1han/sam/network/ServerSessions.java`
- 以下のpacket表で「新規」とした5ファイル
- `src/test/java/jp/me1han/sam/network/NetworkVerificationTest.java`
- `NETWORK_REVIEW.md`

## 2. 削除ファイル

- `src/main/java/jp/me1han/sam/network/PacketDebugAnnounceEvent.java`
- `src/main/java/jp/me1han/sam/DepartureEvents.java`（汎用の `network/ServerTaskQueue.java` へ置換）

いずれもGit管理下の旧ファイルなので、履歴から復元可能。

## 3–6. Packet一覧・discriminator・方向・payload

共通STARTヘッダー: `sessionId:long`, `linkKey`, `priority:int`, `allowOverlap:boolean`, `playLocalSound:boolean`, origin `x/y/z:int`, Speaker座標IDの `long[]`。

座標IDは X/Z 各26bit、Y 12bit。負のX/Z、y=0、Minecraftのワールド境界に対応。STARTにSpeakerのrange/volumeやdebug情報は含まない。

| ID | Packet | 方向 | 主なpayload |
| --- | --- | --- | --- |
| 0 | PacketAnnounce | S2C | 共通STARTヘッダー、startMelo、bodySounds、arrMelo。通常放送とAwareness |
| 1 | PacketConfig | C2S | 対象TE x/y/z、scriptName、linkKey、playLocalSound |
| 2 | PacketTrainTypeConfig | C2S | x/y/z、conditions(key/type)、linkKey、isControlCar |
| 3 | PacketDebugConfig | C2S | x/y/z、linkKey。dataMap用DebugReceiverのGUI設定 |
| 4 | PacketStartAnnouncerConfig | C2S | x/y/z、linkKey、isControlCar |
| 5 | PacketStopAnnouncerConfig | C2S | x/y/z、linkKey、isControlCar |
| 6 | PacketSpeakerConfig | C2S | x/y/z、linkKey、range、volume。GUI保存時のみ |
| 7 | 廃止・未使用 | — | 旧PacketDebugAnnounceEvent。再利用しない |
| 8 | PacketAwarenessConfig | C2S | x/y/z、linkKey、soundList、interval、randomOrder、allowOverlap、playAfterDeparture、departureDelay |
| 9 | PacketDepartureMelodyConfig | C2S | x/y/z、linkKey、legacy soundId、scriptName |
| 10 | PacketDepartureControl | S2C | sessionId、cancel。false=RELEASE/OFF、true=CANCEL。payload 9bytes |
| 11 | PacketDepartureSwitchConfig | C2S | x/y/z、linkKey、modelName、rotationYaw |
| 12 | PacketAnnounceStop（新規） | S2C | sessionIdのみ。8bytes。ID=0は管理者global stop専用 |
| 13 | PacketDepartureStart（新規） | S2C | 共通STARTヘッダー、alternate、finishChorus、melody、doorClose、各duration、interval。通常放送sequenceはシリアライズしない |
| 14 | PacketSessionFinished（新規） | C2S | sessionIdのみ。8bytes。session終了・priorityによる不採用/置換の通知 |
| 15 | PacketMissingSpeakers（新規） | C2S | sessionId、未解決Speaker座標ID配列。例外時のみ、受信者/sessionごとに一度 |
| 16 | PacketSpeakerFallback（新規） | S2C | sessionId、要求された未解決Speakerの座標ID/range/volumeのみ |

ID 14は音声ごとのPLAY/debug通知ではない。bodySoundsが複数でもsessionの自然終了時に一度だけ返す。Awarenessの優先度待機やclientごとの開始時刻差を含めてrecipientを解放するために使う。C2Sから削除できるのは、その送信者自身がSTARTを受信したsessionのrecipient記録だけ。

ID 15/16は描画距離外やTE同期未完了に対応する例外経路。正常なTE同期済み再生では発生しない。serverはSTARTで当人に送った座標IDと現在のRegistryを照合するため、任意座標の情報取得やTE生成には使用できない。再要求は無視し、全Speaker設定の再送や周期同期はしない。

旧0.1.5-beta/v0.1.5-betaとPacketFixのwire形式は非互換。0.2.1-betaではForge標準の厳密バージョン照合を設定したため、server/client双方を0.2.1-betaへ更新すること。独立したprotocol番号や追加handshakeは使用しない。

## 7–8. 配送API

`sendToDimension` は本番ソース内に残していない。

- `TileEntityAnnouncer.startAnnounce`, `startDirectSound`（Awarenessを含む）, `startDeparture` は `ServerSessions.start` に集約。
- 複数Speakerがある場合は、各player向けのSTARTを `sendTo` で一回だけ送信。
- Speakerがなく `playLocalSound` だけの場合は `sendToAllAround` + `TargetPoint`。radiusは16+2 blocks。
- `forceStop`, owner unload/invalidate, Departure OFF/CANCEL は記録済みrecipientへ `sendTo`。
- `/sam stopall` だけは `sendToAll(PacketAnnounceStop(0))` を使用。

## 9–10. Registryとライフサイクル

```text
IdentityHashMap<World, DimensionSpeakers>
  DimensionSpeakers
    HashMap<Long, Entry> byPosition
    HashMap<String, HashMap<Long, Entry>> byLinkKey
```

Worldはdimensionを識別すると同時に、Integrated Serverの別ワールド再入場で同dimensionの古いentryを混ぜないためのキー。Registryはlogical serverのみからアクセスする。

- `validate()` → register。
- `invalidate()` / `onChunkUnload()` → unregister。
- GUI保存による実変更 → `TileEntitySpeaker.applyConfig()` → register更新 → dirty/update。
- 配置済みTEへのNBT設定コピー → readFromNBT後に更新。ロード時にworldが未設定ならvalidateで登録。
- 同一座標の古いTEが遅れてinvalidateされても、TE identityを照合するため新しいTEを削除しない。
- `canUpdate() == false`。Speakerの `updateEntity()` overrideは削除し、通常tickではRegistryに触らない。
- World unload / server停止でRegistry全体を解放。

`LoadedSamTiles` はSpeakerとは別に、SAMの制御TEだけを保持する小さなserver登録一覧。Awarenessの親検索、発車スイッチ連携、Start/Stop、TrainType/dataMapでRTMを含む全TEを走査しないために導入した。ネットワークtopologyの複製やrevision管理ではなく、tick処理も持たない。

## 11–13. Sessionとrecipient

serverはSTARTごとに単調増加するlong IDを発行する。`Map<Long, Session>` でowner/world/linkKey/priority/recipient集合を記録し、UUID→session集合の逆引きでlogout/dimension変更/respawn時に掃除する。補完要求の認可用に、各recipientへ実際に送った座標IDを保持し、一度の要求処理後にその記録を解放する。

recipientはdimension内のplayerと対象linkKeyのSpeakerだけを比較して決定する。Speakerのrange+2 blocksの球内、またはローカル音源の16+2 blocksの球内にいれば受信者に含める。個別STARTにはそのplayerの近くのSpeaker座標だけを入れる。複数範囲に入ってもplayerは一度だけ追加される。

STOP/CANCELは現在の位置でrecipientを再計算しない。移動後でも接続が存続し同じWorldにいれば配送する。別worldへの移動・logout・respawnはrecipientから外す。STOP/CANCEL、最後の終了通知、owner unload、World unload、server停止、global stop、24時間相当の保険TTLで記録を解放する。

clientは `Map<Long, AnnounceSession>` を使用。priority判定にはlinkKeyを使うが、操作packetの対象はsession IDだけ。同じIDのSTART再受信は無視する。World identityが変わったときは音声・session・未解決座標・補完情報を解放する。

## 14–15. Validationとthread dispatch

全GUI C2S handlerは `ConfigAccess.enqueue` を通す。network threadではsenderとWorld/connectionのidentityだけを保持し、World/TEの検索・変更は `ServerTaskQueue` がlogical server tick STARTで実行する。

適用前の検証:

1. senderが生存、同じWorld/connection、接続中、server側World。
2. `blockExists`。検証のためにchunkをロードしない。
3. 対象TEの型、valid状態、World identity。
4. TE中心から距離8 blocks以内（Container相当の距離検証）、`canPlayerEdit`、`world.canMineBlock`。
5. String/List/numeric上限。
6. 正常な対象TEが確認できた後にだけ値を更新。
7. Speakerは比較して実変更時だけRegistry/dirty/update。その他の単純設定はNBT比較でdirty/updateを省略。AwarenessとDepartureは同値時にtimer/playbackをリセットしない。

上限は `PacketLimits` に集約:

| 対象 | 上限/許容範囲 |
| --- | --- |
| linkKey | 64文字 |
| scriptName / soundId / condition key | 256文字 |
| switch modelName | 128文字、登録モデルに限る |
| TrainType conditions | 64件、type=0〜3 |
| Awareness soundList | 8192文字、256音まで、各soundId 256文字 |
| Awareness interval | 20〜1,728,000 ticks |
| departureDelay | 0〜1,728,000 ticks |
| Speaker range | 1〜128 blocks |
| Speaker volume | finite、0.0〜MAX_VOLUME（現在1.0） |
| Speaker座標配列 | START/Missing/fallbackとも512件まで、残りbuffer bytesも検証 |
| bodySounds | 256件 |

UTF-8の長さを文字列確保前に検証し、文字数も検証する。負のList sizeや巨大なList sizeはdecodeで拒否する。GUIはDone/保存時だけ送信する。Speaker GUIは不正数値を送らず、server確認前のTEへの仮書き込みも削除した。

server queueは待機1024件、tickごとの処理256件に制限。overflowでFINISHEDがdropされても、queueとは独立した200tick周期のTTL掃除で永久残存を防ぐ。音声制御S2Cは従来どおりclient `pending` へ積み、ClientTickからWorld/SoundHandler/sessionを操作する。

## 16. 削除したdebug/fallback

- `PacketDebugAnnounceEvent` とdiscriminator 7登録・空handler。
- START/STOP/PLAY/SPEAKER_CHECK/NO_MATCHのdebug C2Sと、そのためだけのdetail生成。
- `serverTotalSpeakers`, `serverSampleKeys`, `sampleKeys`, `countByDimension`。
- `collectSpeakersByKey`, `containsSpeaker`, SERVER_SCAN。
- server/clientの `world.loadedTileEntityList` によるSpeaker探索と古い全Speaker cache。
- Start/Stop Announcerの空debug出力関数呼び出しと文字列組み立て。

dataMap用DebugReceiverそのものは維持している。

## 17. 計算量

S=全Speaker数、K=対象linkKeyのSpeaker数、T=dimension内全TE数、P=dimension内player数、R=対象recipient数。

| 処理 | 旧 | 新 |
| --- | --- | --- |
| Speaker通常tickのRegistry更新 | 全体でO(S²)/tick | なし |
| 座標register/update/remove | O(S) | 平均O(1) |
| linkKey検索 | O(S) | Map検索+対象の列挙O(K) |
| 放送開始のSpeaker収集 | Registry走査+O(T)+最大O(K²)重複照合 | O(K)、全TE走査なし |
| 受信者選定 | dimension全員 | O(P×K)、Set/Mapで重複排除 |
| START packet数 | P | R |
| STARTのSpeaker情報 | 各playerへ20×K bytes+debug文字列等 | 各recipientへ8×そのplayerの対象Speaker数 bytes |
| clientのSpeaker解決 | 全TE cache/fallback走査 | 送られた座標だけgetTileEntity |

新方式でも放送開始時のrecipient計算はplayer数×対象Speaker数に比例する。通常時の全Speaker更新と遠距離playerへの通信をなくすことが主な改善であり、無条件にすべてO(1)になるわけではない。

## 18. 発車メロディ・再生仕様の維持

`DepartureProgram` / `DepartureSequence` の実装は変更していない。serverのphaseStartedTick/releasedTick、clientのsequenceChangedThisTick/releasedThisTickを維持。STARTとOFFが同じclient tickに届いた場合は初期化してからreleaseする。

melody/doorClose/interval、alternate、finishChorus、channelごとのstop、priority=20、Awareness=0/通常=10、Awarenessの待機/allowOverlapを保持した。スイッチのmode/yaw/portable metadata、TrainType/dataMapの処理も維持している。

TE自体が未解決、またはSpeaker TEインスタンスは存在してもserverのS35設定をまだ適用していない場合は、指定座標だけを再確認し、未解決座標を一度だけ補完要求してsession内で再利用する。`TileEntitySpeaker.onDataPacket`で設定NBTを適用し終えたTEだけをauthoritativeとし、その時点でfallbackを削除する。同期済みTEは現在のlinkKey一致時だけ現在のrange/volumeで再生し、不一致・正式な空linkKeyではfallbackを使わず再生しない。`readFromNBT`単独では同期済みと判定しない。sequenceの時計は止めないため、極端な遅延で既に終了した音を後から再生し直すことはしない。遅延中の音声の冒頭まで完全に復元する方式ではない。遅れて開始した音も次の通常音への遷移、channel終了、STOP/CANCELで停止する。

## 19. Test/verification

実行: `gradlew.bat test check --offline --console=plain`。

- `test`: 成功。
- `verifyDeparture`: 190 checks成功。
- `verifySwitchModels`: 177 checks成功。
- `verifyNetwork`: 162 checks成功。
- `check`: 成功。
- `git diff --check`: 問題なし。

既存のテストはmainベースのverificationが主体であり、`test`だけではなく`check`に登録された全verificationを実行した。negative testで出る不正durationのERRORログは想定済みの検証出力。Java 8のheadless player fixtureに既存テスト同様のReflectionFactoryを使うため、test compile時に内部API warningが出る。

追加テストは150台のSpeakerに100回ずつidle updateを呼び、Registry entryが入れ替わらないことを確認する。fixtureのloadedTileEntityListはiteratorを呼ぶと失敗するようにして、配送・dataMap・client再生の全TE走査を検出する。

そのほか、validate/unload/reload/二重validate/旧TEの遅延invalidate、同値GUI保存、存在しない座標、遠距離/編集拒否/World変更、NaN/Infinity/巨大List/String、周辺3人と遠方10人の配送、移動後STOP、旧CANCEL、logout/dimension変更、client pending、同tick START/OFF、Awareness/allowOverlap、未解決TE再試行、限定fallback、session単位の終了通知、自然終了での音声解放を検証した。

確認はheadless実処理テスト・既存の発車メロディ/スイッチverification・静的検査。実際のDedicated/Integrated Serverを起動して複数ゲームclientで音を聴く検証や、巨大RTMワールドでの実測は行っていない。

## 通信例: 放送1回・Speaker10台・周辺3人・同dimension遠方10人

全Speakerが同じlinkKey、周辺3人は10台すべての対象範囲に入り、TE同期済み、priorityによる拒否なし、通常音1個で自然終了する例。Minecraft/Forgeのフレームヘッダーや通常chunk/TE同期は以下のpacket数に含めない。

| 段階 | 旧実装 | 新実装 |
| --- | --- | --- |
| START S2C | 周辺3+遠方10の13人へ各1通、計13通 | 周辺3人へ各1通、計3通。遠方は0通 |
| START内のSpeaker情報 | 各通10台のx/y/z/range/volume=200bytes、さらにdebug情報 | 各通10台の座標ID=80bytes、range/volume/debugなし |
| client START debug C2S | 13通 | 0通 |
| client SPEAKER_CHECK C2S | 13通 | 0通 |
| client PLAY debug C2S | 音1個につき13通。serverProvided情報があるため遠方clientも返す | 0通 |
| 自然終了 | 終了専用通知なし | 周辺3人からsession終了通知各1通、計3通 |
| 上記合計 | 52通 | 6通 |

STARTだけなら **13通→3通**、Speaker部分の合計payloadは **2600bytes→240bytes**。新実装はSpeaker10台×player3人=30通に分割しない。音声が複数でも終了通知は一つのsessionにつき各recipient一度だけ。

再生途中でforceStopする場合は、旧実装ではdimensionの13人へSTOP S2Cと、停止対象sessionが残るclientからSTOP debug C2S。新実装では記録済み3人へ8bytesのsession STOPを各1通送り、終了通知を待たずserver記録を解放する。周辺から遠くへ移動した元recipientもこの3人に含む。

TEが未解決だった場合だけ、該当clientごとに補完要求1通+応答最大1通が追加される。10台まとめて一往復であり、Speakerごとに10往復にはしない。

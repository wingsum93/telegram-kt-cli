# Telegram fetcher kt cli
XXXX is a cli telegram client to fetch messages of telegram. User need to login first in order to fetch the history messages. 


## Architecture
1) Auth + session

用 TDLib login（phone + code + optional 2FA password）

Session/database 存喺 data/tdlib/（唔好 commit）

2) History fetcher (pagination)

目標：由最新一路掃到最舊（或者由某個 checkpoint 開始）

使用 TDLib：getChatHistory(chatId, fromMessageId, offset, limit, onlyLocal=false)

每批 100（或 200）條，逐批落 jsonl

3) Media downloader (async)

每條 msg 如果有 media：extract TDLib fileId

call downloadFile(fileId, priority, offset=0, limit=0, synchronous=false)

透過 updateFile 監聽 download 完成，攞到 local path，然後 copy 到你嘅 export media folder

之後 optionally delete TDLib cache copy（避免重覆佔空間）



CLI flags (你會多謝自己)

Example:

--channel @xxx

--out export/xxx

--tdlib data/tdlib

--resume

--since 2024-01-01

--max-messages 50000

--download-media true

--max-parallel-download 3

--delay-ms 300



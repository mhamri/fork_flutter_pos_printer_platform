## 2.0.0

* Breaking: `PrinterConnector.send` and `PrinterManager.send` return a `PrintResult` (`PrintConfirmed`, `PrintUnconfirmed` or `PrintFailed`) instead of a `bool`. See README.md, "Print results".
* Network: `TcpPrinterConnector.print` opens a socket for each job and asks the printer for its status after the job. The shared socket is gone. See README.md, "Network printing".
* Android USB: the send waits for every `bulkTransfer`, checks how many bytes each one took, and sends the right length for the last piece. It no longer reports success before the write.
* Android Bluetooth Classic: a failed write fails the job instead of showing a toast.
* Android BLE: the job goes in pieces that fit the MTU, and each piece waits for the printer.
* Android: sends run on a worker thread, not the UI thread.
* Removed the `sendText`, `printText` and `printRawData` Android methods, which no Dart code called.

## 1.1.0

* Toast msgs english locale default

## 1.0.12

* Resolve minor bug [Android] 12

## 1.0.11

* Resolve minor bug dependecies

## 1.0.10

* Now android supports targetSdkVersion 31

## 1.0.9

* Resolve minor bug [Android] connection

## 1.0.8

* Resolve minor bug [Android] connection

## 1.0.6

* Resolve minor bug [Android] connection

## 1.0.6

* Resolve minor bug windows printer

## 1.0.5

* Get current status bt

## 1.0.4

* Solved Bug windows: USB

## 1.0.3

* Bug android notify events: Bluetooth 

## 1.0.2

* Bug android connection interface: USB 

## 1.0.1

* How to use it.

## 1.0.0

* Initial release.

enum PrinterProblem { coverOpen, paperOut, error }

// See README.md, "Print results".
sealed class PrintResult {
  const PrintResult();

  bool get reachedPrinter => this is! PrintFailed;
}

final class PrintConfirmed extends PrintResult {
  final Set<PrinterProblem> problems;

  const PrintConfirmed([this.problems = const {}]);

  @override
  String toString() => problems.isEmpty ? 'Printer confirmed the job' : 'Printer confirmed the job and reports ${problems.map((p) => p.name).join(', ')}';
}

final class PrintUnconfirmed extends PrintResult {
  final String reason;

  const PrintUnconfirmed(this.reason);

  @override
  String toString() => reason;
}

final class PrintFailed extends PrintResult {
  final String reason;

  const PrintFailed(this.reason);

  @override
  String toString() => reason;
}

const List<int> offlineCauseStatusRequest = [0x10, 0x04, 0x02];

// ESC/POS DLE EOT 2 reply: bits 1 and 4 are always 1, bits 0 and 7 always 0.
// Automatic status back and XON/XOFF bytes never match this shape.
bool isOfflineCauseStatus(int byte) => byte & 0x93 == 0x12;

Set<PrinterProblem> problemsInOfflineCauseStatus(int status) => {
  if (status & 0x04 != 0) PrinterProblem.coverOpen,
  if (status & 0x20 != 0) PrinterProblem.paperOut,
  if (status & 0x40 != 0) PrinterProblem.error,
};

PrintResult printResultFromPlatformReply(Object? reply) {
  if (reply is! Map) return PrintFailed('Printer plugin gave no result: $reply');
  final reason = reply['reason'] as String?;
  final status = reply['status'] as int?;
  return switch (reply['outcome']) {
    'delivered' when status != null => PrintConfirmed(problemsInOfflineCauseStatus(status)),
    'delivered' => const PrintConfirmed(),
    'sent' when status != null => PrintConfirmed(problemsInOfflineCauseStatus(status)),
    'sent' => PrintUnconfirmed(reason ?? 'Every byte was sent, but the printer did not confirm the job'),
    _ => PrintFailed(reason ?? 'Printer did not take the job'),
  };
}

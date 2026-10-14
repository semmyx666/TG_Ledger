#!/usr/bin/env python3
"""Source regression guards for the reported crash/navigation defect; not an Android test."""
from pathlib import Path
import json
R=Path(__file__).resolve().parents[1]
A=R/'app/src/main/kotlin/com/semmyx/ledger'
root=(A/'navigation/AppRoot.kt').read_text()
analytics=(A/'analytics/AnalyticsScreen.kt').read_text()
bank=(A/'bank/BankScreen.kt').read_text()
repo=(R/'data/src/main/kotlin/com/semmyx/ledger/data/bank/BankImportRepository.kt').read_text()
checks=[]
def check(condition,label):
 assert condition,label
 checks.append(label)
check('CompositionLocalProvider(LocalActivityResultRegistryOwner provides host, LocalOnBackPressedDispatcherOwner provides host)' in root,'Explicit real Activity owners are provided above localized content')
check(root.index('LocalActivityResultRegistryOwner provides host') < root.index('LocalizedContent(state.preferences.language)'),'Registry owner is installed before the localized Context replacement')
check('TextButton(onClick = onHistory, modifier = Modifier.weight(1f)) { Icon(Icons.Default.History' in analytics,'History button no longer invokes dashboard back action')
check('composable("history?month={month}"' in root and 'HistoryScreen(historyModel' in root,'History has an independent navigation destination')
check('onHistory = { month -> nav.navigate(' in root,'Analytics selected month is passed to history')
check('fun openSettings(action: String' in bank and 'Intent.FLAG_ACTIVITY_NEW_TASK' in bank,'System settings launched safely from localized non-Activity Context')
check('repository.importText(bankId, body)' in bank and 'preview = remember(bankId, body)' in bank,'Local text recovery has a preview and explicit submit action')
check('if (!enabled && !manual)' in repo and 'if (!manual && autoImport' in repo,'Manual recovery works without automation and never bypasses confirmation')
check('model.diagnostics.collectAsStateWithLifecycle()' in bank,'Live import diagnostics are visible in the banking screen')
check('requestRebind' in bank and 'repository.configuredPackage(bankId)' in bank,'Reconnect and bank package configuration are provided')
result={'kind':'source-regression-guards','passed':len(checks),'checks':checks,'compiled_android':False,'device_crash_fixed_verified':False}
(R/'verification/navigation-recovery.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
print(json.dumps(result,ensure_ascii=False,indent=2))

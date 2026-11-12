"""Run real controls, always replacing stale evidence, including failures."""
from pathlib import Path
from datetime import datetime, timezone
import json
import sys
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from ecoshield.model import YamnetAnalyzer


def save_report(root, report):
    destination = root/'reports'
    destination.mkdir(parents=True, exist_ok=True)
    (destination/'audio_control_results.json').write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding='utf-8')
    lines = ['# Проверка аудио на контрольных примерах', '',
             f"Начало: {report['started_at']}. Завершён: {report['completed']}. Успех: {report['success']}.", '',
             'Готовая YAMNet, без дообучения. Пороги исходные, не подогнаны по этим примерам. Ошибки выполнения сохраняются.', '',
             '| Файл | Метка источника | События | Оценки лодка / выстрел / двигатель | Задержка мс |',
             '|---|---|---|---|---|']
    for record in report['records']:
        item = record['sample']
        if 'error' in record:
            lines.append(f"| {Path(item['file']).name} | {item['label']} | ERROR: {record['error']} | — | — |")
            continue
        result = record['result']
        kinds = ', '.join(sorted(set(event['type'] for event in result['events']))) or result['status']
        scores = result['target_scores']
        values = ' / '.join(f'{scores.get(name, 0):.3f}' for name in ('boat_suspected', 'gunshot_suspected', 'engine_detected'))
        lines.append(f"| {Path(item['file']).name} | {item['label']} | {kinds} | {values} | {result['latency_ms']} |")
    if report.get('error'):
        lines += ['', 'Ошибка прогона: '+report['error']]
    lines += ['', 'Выборка мала и демонстрирует исполнение, а не качество на Каспии. gunshot_effect — звуковой эффект, не подтверждённая полевая запись выстрела. Примеры получены из сжатых CC0 previews; надводный микрофон отличается от гидрофона.',
              '', 'Успех прогона означает, что все файлы обработаны без ошибки выполнения; он не означает правильную классификацию каждого файла. Сравнивайте метку источника и события в таблице.',
              '', 'Score не является вероятностью браконьерства. Начальные пороги не калиброваны. Полные результаты и источники: audio_control_results.json и data/manifest.json.']
    (destination/'AUDIO_CONTROLS.md').write_text('\n'.join(lines)+'\n', encoding='utf-8')


def run_evaluation(root=ROOT, analyzer_factory=YamnetAnalyzer):
    root = Path(root)
    report = {'started_at': datetime.now(timezone.utc).isoformat(), 'success': False, 'completed': False, 'records': []}
    save_report(root, report)
    try:
        manifest = json.loads((root/'data/manifest.json').read_text(encoding='utf-8'))
        analyzer = analyzer_factory()
        for item in manifest['samples']:
            record = {'sample': item}
            try:
                result = analyzer.analyze((root/item['file']).read_bytes())
                record['result'] = result
                print(Path(item['file']).name, item['label'], result['target_scores'], result['latency_ms'], flush=True)
            except Exception as error:
                record['error'] = f'{type(error).__name__}: {error}'
                print(Path(item['file']).name, record['error'], flush=True)
            report['records'].append(record)
            save_report(root, report)
        report['completed'] = True
        report['success'] = bool(report['records']) and all('error' not in record for record in report['records'])
    except Exception as error:
        report['error'] = f'{type(error).__name__}: {error}'
    finally:
        report['finished_at'] = datetime.now(timezone.utc).isoformat()
        save_report(root, report)
    return report


if __name__ == '__main__':
    raise SystemExit(0 if run_evaluation()['success'] else 1)

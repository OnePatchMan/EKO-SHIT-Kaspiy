"""Local HTTP contract consumed by a Java backend. Run through uvicorn."""
from contextlib import asynccontextmanager
import logging
from typing import Annotated
from uuid import uuid4
from fastapi import Body, FastAPI, File, Form, HTTPException, UploadFile
from starlette.concurrency import run_in_threadpool
from . import __version__
from .audio import MAX_BYTES
from .forecast import forecast_conditions
from .iot import validate_batch
from .validation import identifier, iso, timestamp
from .vision import MAX_IMAGE_BYTES, compare_images

AUTO = object()
logger = logging.getLogger('ecoshield')


def create_app(analyzer=AUTO):
    @asynccontextmanager
    async def lifespan(app):
        if analyzer is AUTO:
            try:
                from .model import YamnetAnalyzer
                app.state.analyzer = await run_in_threadpool(YamnetAnalyzer)
            except Exception:
                logger.exception('YAMNet failed to initialize; audio requests will return HTTP 503')
                app.state.analyzer = None
        yield

    app = FastAPI(title='EcoShield AI — hackathon demonstrator', version=__version__, lifespan=lifespan,
                  description='Real YAMNet audio inference, baseline RGB changes, IoT validation and explicitly unvalidated spawning-conditions scenarios. Java owns storage and alerts.')
    app.state.analyzer = None if analyzer is AUTO else analyzer

    @app.get('/health')
    def health():
        current = app.state.analyzer
        return {'status': 'ready' if current is not None else 'partial', 'version': __version__,
                'audio_model_ready': current is not None, 'audio_model_version': getattr(current, 'version', None),
                'forecast_mode': 'scenario_conditions_forecast', 'biological_forecast_validated': False}

    @app.post('/v1/audio/analyze')
    def audio_analyze(file: Annotated[UploadFile, File(description='Mono/stereo WAV, 1–60 s, up to 10 MiB')],
                      station_id: Annotated[str, Form()], recorded_at: Annotated[str, Form()]):
        try:
            station = identifier(station_id)
            recorded = iso(timestamp(recorded_at))
            data = file.file.read(MAX_BYTES+1)
            if len(data) > MAX_BYTES:
                raise HTTPException(413, 'audio exceeds 10 MiB')
            current = app.state.analyzer
            if current is None:
                raise HTTPException(503, 'audio model unavailable; check model assets and server logs')
            result = current.analyze(data)
        except ValueError as error:
            raise HTTPException(422, str(error)) from error
        return {'analysis_id': str(uuid4()), 'station_id': station, 'recorded_at': recorded, **result}

    @app.post('/v1/spawning/forecast')
    def spawning_forecast(payload: Annotated[dict, Body(description='See examples/forecast_request.json; all observations need timezones and explicit provenance')]):
        try:
            return forecast_conditions(payload)
        except ValueError as error:
            raise HTTPException(422, str(error)) from error

    @app.post('/v1/iot/validate')
    def iot_validate(payload: Annotated[dict, Body(description='Validate only; this service does not store sensor readings')]):
        try:
            return validate_batch(payload)
        except ValueError as error:
            raise HTTPException(422, str(error)) from error

    @app.post('/v1/images/compare')
    def images_compare(before: Annotated[UploadFile, File()], after: Annotated[UploadFile, File()],
                       station_id: Annotated[str, Form()], before_at: Annotated[str, Form()],
                       after_at: Annotated[str, Form()], aligned: Annotated[bool, Form()] = False,
                       is_demo: Annotated[bool, Form()] = True):
        try:
            station = identifier(station_id)
            earlier, later = timestamp(before_at), timestamp(after_at)
            if later <= earlier:
                raise ValueError('after_at must be later than before_at')
            a, b = before.file.read(MAX_IMAGE_BYTES+1), after.file.read(MAX_IMAGE_BYTES+1)
            if len(a) > MAX_IMAGE_BYTES or len(b) > MAX_IMAGE_BYTES:
                raise HTTPException(413, 'image exceeds 10 MiB')
            result = compare_images(a, b, aligned=aligned)
        except ValueError as error:
            raise HTTPException(422, str(error)) from error
        return {'analysis_id': str(uuid4()), 'station_id': station, 'before_at': iso(earlier),
                'after_at': iso(later), 'is_demo': is_demo, **result}

    return app


app = create_app()

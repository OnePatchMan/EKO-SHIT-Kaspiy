"""Baseline RGB change candidates. Does not classify nets, pollutants or boats."""
import io
import warnings
import numpy as np
from PIL import Image, UnidentifiedImageError
from scipy import ndimage

MAX_IMAGE_BYTES = 10 * 1024 * 1024
MAX_PIXELS = 4_000_000


def _read_image(data):
    if not data or len(data) > MAX_IMAGE_BYTES:
        raise ValueError('image must be nonempty and no larger than 10 MiB')
    try:
        with warnings.catch_warnings():
            warnings.simplefilter('error', Image.DecompressionBombWarning)
            with Image.open(io.BytesIO(data)) as image:
                if image.format not in ('PNG', 'JPEG') or image.width * image.height > MAX_PIXELS:
                    raise ValueError('expected PNG/JPEG up to 4 megapixels')
                return np.asarray(image.convert('RGB'), dtype=np.float32)
    except (UnidentifiedImageError, OSError, Image.DecompressionBombError, Image.DecompressionBombWarning) as error:
        raise ValueError('cannot decode supported image') from error


def compare_images(before_data, after_data, aligned=False):
    if aligned is not True:
        raise ValueError('images must be spatially aligned before comparison; pass aligned=true only after registration')
    before, after = _read_image(before_data), _read_image(after_data)
    if before.shape != after.shape:
        raise ValueError('aligned images must have identical dimensions')
    # Remove a uniform brightness shift; clouds, shadows and seasonal differences remain confounders.
    delta = after - before
    delta -= np.median(delta, axis=(0, 1), keepdims=True)
    changed = np.mean(np.abs(delta), axis=2) >= 30.0
    labels, count = ndimage.label(changed)
    regions = []
    for label_id, region in enumerate(ndimage.find_objects(labels), 1):
        if region is None:
            continue
        area = int(np.count_nonzero(labels[region] == label_id))
        if area >= 16:
            ys, xs = region
            regions.append({'x': xs.start, 'y': ys.start, 'width': xs.stop-xs.start, 'height': ys.stop-ys.start, 'changed_pixels': area})
    regions.sort(key=lambda region: region['changed_pixels'], reverse=True)
    return {'method': 'registered_rgb_difference_v0.1', 'width': before.shape[1], 'height': before.shape[0],
            'changed_fraction': round(float(changed.mean()), 6), 'candidate_regions': regions[:100],
            'region_count': len(regions), 'detects_illegal_nets_or_spills': False,
            'requires_inspector_review': True, 'trained_model': False,
            'warnings': ['Requires aligned RGB images with comparable resolution and illumination.',
                         'Clouds, shadows, waves and seasonal changes may cause false candidates. No geolocation or legal conclusion is inferred.']}

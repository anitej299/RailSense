import math
import unittest
from io import StringIO
from unittest.mock import Mock
from scripts.train_mobile_centroid import extract, train, validate, score, synthetic, windows, read_csv


class MobileModelTest(unittest.TestCase):
    def test_v3_export_skips_non_sensor_records(self):
        path = Mock()
        path.open.return_value = StringIO('record_type,monotonic_ns,accel_x,accel_y,accel_z,gyro_x,gyro_y,gyro_z\n'
                                         'event,,,,,,,\n'
                                         'sensor,1000000000,0,0,9.81,0,0,0\n')
        rows = list(read_csv(path))
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0][0], 1000000000)
    def test_kotlin_feature_contract_sine(self):
        rows = [(i * 20_000_000, 2 * math.sin(2 * math.pi * 5 * i / 50), 0, 9.81, 0, 0, 0) for i in range(100)]
        f = extract(rows)
        self.assertAlmostEqual(f[0], math.sqrt(2), places=6)
        self.assertAlmostEqual(f[5], 10, places=6)
        self.assertGreater(f[6], .9)

    def test_training_round_trip_and_invalid_model(self):
        features = list(windows(synthetic(50, 16), 50, 2))
        model = train(features, 50, 2, True)
        validate(model)
        self.assertEqual(score(model, model['means']), 0)
        self.assertTrue(all(0 <= score(model, row) <= 1 for row in features))
        model['scales'][0] = 0
        with self.assertRaises(AssertionError):
            validate(model)

    def test_timestamp_failures(self):
        with self.assertRaises(ValueError):
            extract([(0, 0, 0, 9.81, 0, 0, 0)] * 100)


if __name__ == '__main__':
    unittest.main()

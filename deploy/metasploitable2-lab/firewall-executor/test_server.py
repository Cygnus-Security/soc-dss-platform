import ipaddress
import unittest

import server


class TargetAllowlistTest(unittest.TestCase):
    def test_accepts_private_dataset_targets(self):
        for value in ("10.229.0.4", "172.31.252.10", "192.168.10.25"):
            with self.subTest(value=value):
                self.assertTrue(server._target_allowed(ipaddress.ip_address(value)))

    def test_rejects_control_plane_even_inside_private_cidr(self):
        for value in ("172.31.253.2", "172.31.253.3"):
            with self.subTest(value=value):
                self.assertFalse(server._target_allowed(ipaddress.ip_address(value)))

    def test_rejects_public_and_non_ipv4_targets(self):
        for value in ("8.8.8.8", "2001:db8::1"):
            with self.subTest(value=value):
                self.assertFalse(server._target_allowed(ipaddress.ip_address(value)))

    def test_parser_rejects_non_rfc1918_network(self):
        with self.assertRaisesRegex(ValueError, "non-RFC1918"):
            server._parse_allowed_target_cidrs("10.0.0.0/8,8.8.8.0/24")


if __name__ == "__main__":
    unittest.main()

import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import suite


class ExternalInputsTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        self.cache=patch.object(suite,'CACHE',self.root/'cache');self.cache.start();self.addCleanup(self.cache.stop)
        self.source=patch('suite.source_root',return_value=self.root);self.source.start();self.addCleanup(self.source.stop)

    def record(self):
        data=io.BytesIO()
        with zipfile.ZipFile(data,'w') as jar:jar.writestr('META-INF/neoforge.mods.toml','[[mods]]\nmodId="botania"\n')
        blob=data.getvalue()
        return dict(url='https://example.invalid/dependency.jar',file='dependency.jar',sha256=suite.digest(blob),mod_id='botania'),blob

    def declarations(self,record):
        suite.write_json(self.root/'.github/ci-external.json',{role:dict(record) for role in ('botaniaJar','bloodMagicJar','modopediaJar')})

    def test_fm_roles_are_required_and_https_only(self):
        record,_=self.record();self.declarations(record)
        self.assertEqual(len(suite.external_records('fm')),3)
        record['url']='http://example.invalid/file.jar';self.declarations(record)
        with self.assertRaisesRegex(ValueError,'HTTPS'):suite.external_records('fm')
        suite.write_json(self.root/'.github/ci-external.json',{})
        with self.assertRaisesRegex(ValueError,'roles'):suite.external_records('fm')

    def test_unsafe_filename_is_rejected(self):
        record,_=self.record();record['file']='../outside.jar';self.declarations(record)
        with self.assertRaisesRegex(ValueError,'Unsafe'):suite.external_records('fm')

    def test_corrupt_download_is_never_accepted(self):
        record,_=self.record();response=io.BytesIO(b'corrupt');response.geturl=lambda:record['url']
        with patch('suite.urlopen',return_value=response),self.assertRaisesRegex(ValueError,'SHA-256'):
            suite.external_paths({'botaniaJar':record})
        self.assertFalse(list((self.root/'cache').rglob('*.jar')))

    def test_exact_cached_dependency_is_verified_and_reused(self):
        record,data=self.record();response=io.BytesIO(data);response.geturl=lambda:record['url']
        with patch('suite.urlopen',return_value=response) as download:
            first=suite.external_paths({'botaniaJar':record})
            self.assertEqual(first,suite.external_paths({'botaniaJar':record}))
            self.assertEqual(download.call_count,1)
        record['mod_id']='wrong'
        with patch('suite.urlopen') as download,self.assertRaisesRegex(ValueError,'mod ID'):
            suite.external_paths({'botaniaJar':record})
        download.assert_not_called()

    def test_six_component_order_keeps_compact_after_the(self):
        with patch.dict(os.environ,{},clear=True):
            self.assertEqual(suite.stages(),('ui','core','tinkerer','fm','the','compact'))
            self.assertEqual(suite.PUBLIC_STAGES,suite.STAGES)
        with patch.dict(os.environ,SUITE_TARGET='compact'):
            self.assertNotIn('fm',suite.stages())

    def test_external_hash_changes_only_fm_plan(self):
        suite.write_json(suite.CACHE/'lock.json',dict(mode='release',recipe='recipe',runtime={},sources={
            s:dict(version='1.0.0',revision='1'*40,content=s) for s in suite.STAGES},external={'fm':{'botaniaJar':{'sha256':'0'*64}}}))
        with patch('suite.verified',return_value=dict(key='verified',artifacts={})):
            first=suite.plan('fm')['release-key'];ui=suite.plan('ui')['release-key']
            lock=suite.read_json(suite.CACHE/'lock.json');lock['external']['fm']['botaniaJar']['sha256']='1'*64;suite.write_json(suite.CACHE/'lock.json',lock)
            self.assertNotEqual(first,suite.plan('fm')['release-key'])
            self.assertEqual(ui,suite.plan('ui')['release-key'])

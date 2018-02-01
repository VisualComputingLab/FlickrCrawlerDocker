import web
import sys, os
import warnings
import json
import subprocess

warnings.simplefilter('ignore')

urls = (
    '/(.*)', 'Service'
)
app = web.application(urls, globals())

class Service:
        def GET(self, name):
                # web.header('Content-Type', 'application/json')
                web.header('Access-Control-Allow-Origin',      '*')
                web.header('Access-Control-Allow-Credentials', 'true')
                return json.dumps({'message': 'GET OK!'})
        def POST(self, name):
                # web.header('Content-Type', 'application/json')
                web.header('Access-Control-Allow-Origin',      '*')
                web.header('Access-Control-Allow-Credentials', 'true')
                data = web.data()
                #print data
                data = json.loads(data)
                query = data["flickr"]["query"]

                with open('/crawler/config.json', 'w') as outfile:
                    json.dump(data, outfile)

                os.chdir('/crawler/')
                rv = subprocess.call(['java', '-cp', 'FlickrStandaloneCrawler.jar:lib/jettison-1.3.2.jar:lib/mongo-2.9.3.jar', 'flickrstandalonecrawler.FlickrStandaloneCrawler', 'config.json'])
                os.remove('config.json')

                values = {'query': query, 'rv': rv}
                jsonstr = json.dumps(values)

                return jsonstr

if __name__ == "__main__":
    app.run()

#include <jni.h>
#include <GLES2/gl2.h>
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <string>
#include <thread>
#include <vector>
#include "orbit_math.h"

namespace {
struct Vec3 { float x,y,z; };
GLuint program=0, sphereVbo=0, sphereNbo=0, sphereIbo=0, starVbo=0, milkyWayVbo=0, constellationVbo=0;
GLint aPosition=-1,aNormal=-1,aPointColor=-1,aStarSize=-1,uMvp=-1,uModel=-1,uColor=-1,uLight=-1,uLighting=-1,uPointSize=-1,uUsePointStyle=-1;
GLsizei sphereIndexCount=0, starCount=0, milkyWayCount=0, constellationVertexCount=0;
struct StarVertex { float x,y,z,r,g,b,size; };
constexpr int PLANET_COUNT=8, EPHEMERIS_STEPS=73;
std::vector<Vec3> ephemeris(PLANET_COUNT*EPHEMERIS_STEPS);
GLuint ephemerisOrbitVbos[PLANET_COUNT]={};
bool ephemerisReady=false;
int surfaceW=1,surfaceH=1,quality=1;
std::string gpuInfo="GPU não identificada";
std::vector<GLuint> orbitVbos;
float projection[16],view[16],vp[16],model[16],mvp[16];

void identity(float *m){ for(int i=0;i<16;i++)m[i]=0; m[0]=m[5]=m[10]=m[15]=1; }
void multiply(float *o,const float *a,const float *b){
 float t[16]; for(int c=0;c<4;c++)for(int r=0;r<4;r++)t[c*4+r]=a[r]*b[c*4]+a[4+r]*b[c*4+1]+a[8+r]*b[c*4+2]+a[12+r]*b[c*4+3];
 std::copy(t,t+16,o);
}
void perspective(float *m,float fovy,float aspect,float nearZ,float farZ){
 identity(m); float f=1.f/std::tan(fovy*0.5f*0.01745329252f);
 m[0]=f/aspect;m[5]=f;m[10]=(farZ+nearZ)/(nearZ-farZ);m[11]=-1.f;m[14]=(2.f*farZ*nearZ)/(nearZ-farZ);m[15]=0;
}
Vec3 normalize(Vec3 v){float l=std::sqrt(v.x*v.x+v.y*v.y+v.z*v.z);if(l<1e-6f)return {0,0,0};return {v.x/l,v.y/l,v.z/l};}
Vec3 cross(Vec3 a,Vec3 b){return {a.y*b.z-a.z*b.y,a.z*b.x-a.x*b.z,a.x*b.y-a.y*b.x};}
float dot(Vec3 a,Vec3 b){return a.x*b.x+a.y*b.y+a.z*b.z;}
void lookAt(float *m,Vec3 eye,Vec3 center,Vec3 up){
 Vec3 f=normalize({center.x-eye.x,center.y-eye.y,center.z-eye.z});
 Vec3 s=normalize(cross(f,up)); Vec3 u=cross(s,f); identity(m);
 m[0]=s.x;m[4]=s.y;m[8]=s.z;m[1]=u.x;m[5]=u.y;m[9]=u.z;m[2]=-f.x;m[6]=-f.y;m[10]=-f.z;
 m[12]=-dot(s,eye);m[13]=-dot(u,eye);m[14]=dot(f,eye);
}
GLuint shader(GLenum type,const char *src){
 GLuint s=glCreateShader(type);glShaderSource(s,1,&src,nullptr);glCompileShader(s);GLint ok=0;glGetShaderiv(s,GL_COMPILE_STATUS,&ok);
 if(!ok){glDeleteShader(s);return 0;}return s;
}
bool createProgram(){
 const char *vs="uniform mat4 uMVP; uniform mat4 uModel; uniform float uPointSize; uniform int uUsePointStyle; attribute vec3 aPosition; attribute vec3 aNormal; attribute vec3 aPointColor; attribute float aStarSize; varying vec3 vNormal; varying vec3 vPosition; varying vec3 vPointColor; varying float vStarSize; void main(){vec4 w=uModel*vec4(aPosition,1.0);vPosition=w.xyz;vNormal=mat3(uModel)*aNormal;vPointColor=aPointColor;vStarSize=aStarSize;gl_Position=uMVP*vec4(aPosition,1.0);gl_PointSize=uUsePointStyle==1?aStarSize:uPointSize;}";
 const char *fs="precision mediump float; uniform vec4 uColor; uniform vec3 uLight; uniform int uLighting; uniform int uUsePointStyle; varying vec3 vNormal; varying vec3 vPosition; varying vec3 vPointColor; varying float vStarSize; void main(){float shade=1.0;if(uLighting==1){vec3 n=normalize(vNormal);vec3 l=normalize(uLight-vPosition);shade=0.20+max(dot(n,l),0.0)*0.90;}vec4 color=uUsePointStyle==1?vec4(vPointColor,1.0):vec4(uColor.rgb*shade,uColor.a);if(uUsePointStyle==1){vec2 p=gl_PointCoord-vec2(0.5);float d=length(p);if(d>0.5)discard;color.a=1.0-smoothstep(0.32,0.5,d);}gl_FragColor=color;}";
 GLuint v=shader(GL_VERTEX_SHADER,vs),f=shader(GL_FRAGMENT_SHADER,fs);if(!v||!f)return false;
 program=glCreateProgram();glAttachShader(program,v);glAttachShader(program,f);glLinkProgram(program);glDeleteShader(v);glDeleteShader(f);
 GLint ok=0;glGetProgramiv(program,GL_LINK_STATUS,&ok);if(!ok){glDeleteProgram(program);program=0;return false;}
 aPosition=glGetAttribLocation(program,"aPosition");aNormal=glGetAttribLocation(program,"aNormal");aPointColor=glGetAttribLocation(program,"aPointColor");aStarSize=glGetAttribLocation(program,"aStarSize");
 uMvp=glGetUniformLocation(program,"uMVP");uModel=glGetUniformLocation(program,"uModel");uColor=glGetUniformLocation(program,"uColor");
 uLight=glGetUniformLocation(program,"uLight");uLighting=glGetUniformLocation(program,"uLighting");uPointSize=glGetUniformLocation(program,"uPointSize");uUsePointStyle=glGetUniformLocation(program,"uUsePointStyle");return true;
}
void upload(GLuint &id,const void *data,size_t bytes,GLenum target){glGenBuffers(1,&id);glBindBuffer(target,id);glBufferData(target,bytes,data,GL_STATIC_DRAW);}
void createSphere(int latBands,int lonBands){
 std::vector<float> v,n;std::vector<unsigned short> ix;
 for(int lat=0;lat<=latBands;lat++){float th=lat*3.1415926535f/latBands;float st=std::sin(th),ct=std::cos(th);
  for(int lon=0;lon<=lonBands;lon++){float ph=lon*6.283185307f/lonBands;float x=std::cos(ph)*st,y=ct,z=std::sin(ph)*st;v.insert(v.end(),{x,y,z});n.insert(n.end(),{x,y,z});}}
 for(int lat=0;lat<latBands;lat++)for(int lon=0;lon<lonBands;lon++){unsigned short a=(unsigned short)(lat*(lonBands+1)+lon),b=(unsigned short)(a+lonBands+1);ix.insert(ix.end(),{a,b,(unsigned short)(a+1),b,(unsigned short)(b+1),(unsigned short)(a+1)});}
 sphereIndexCount=(GLsizei)ix.size();upload(sphereVbo,v.data(),v.size()*sizeof(float),GL_ARRAY_BUFFER);upload(sphereNbo,n.data(),n.size()*sizeof(float),GL_ARRAY_BUFFER);upload(sphereIbo,ix.data(),ix.size()*sizeof(unsigned short),GL_ELEMENT_ARRAY_BUFFER);
}
void createStars(int count){
 std::vector<StarVertex> v;v.reserve((size_t)count+48);unsigned int seed=2026;
 auto rnd=[&](){seed=1664525u*seed+1013904223u;return (seed&0x00ffffffu)/16777216.f;};
 for(int i=0;i<count;i++){float a=rnd()*6.2831853f,z=rnd()*2.f-1.f,r=43.f+rnd()*4.f,q=std::sqrt(1-z*z);float warm=rnd();float cr=warm>.82f?1.f:.68f,cg=warm>.82f?.78f:.82f,cb=warm>.82f?.55f:1.f;v.push_back({r*q*std::cos(a),r*z,r*q*std::sin(a),cr,cg,cb,.8f+rnd()*1.0f});}
 struct CatalogStar {const char* name;float ra,dec,mag;};
 const CatalogStar bright[]={
 {"Sirius",6.7525f,-16.716f,-1.46f},{"Canopus",6.3992f,-52.696f,-.74f},{"Arcturus",14.261f,19.182f,-.05f},{"Vega",18.6156f,38.784f,.03f},{"Capella",5.2782f,45.998f,.08f},{"Rigel",5.2423f,-8.202f,.13f},{"Procyon",7.655f,5.225f,.34f},{"Betelgeuse",5.9195f,7.407f,.42f},{"Achernar",1.6286f,-57.237f,.46f},{"Hadar",14.063f,-60.373f,.61f},{"Altair",19.8464f,8.868f,.77f},{"Acrux",12.4433f,-63.099f,.76f},{"Aldebaran",4.5987f,16.509f,.85f},{"Spica",13.4199f,-11.161f,.98f},{"Antares",16.4901f,-26.432f,1.06f},{"Pollux",7.7553f,28.026f,1.14f},{"Fomalhaut",22.9608f,-29.622f,1.16f},{"Deneb",20.6905f,45.280f,1.25f},{"Regulus",10.1395f,11.967f,1.35f},{"Polaris",2.5303f,89.264f,1.98f},
 {"Bellatrix",5.4189f,6.35f,1.64f},{"Alnitak",5.6793f,-1.94f,1.74f},{"Alnilam",5.6036f,-1.20f,1.69f},{"Mintaka",5.5334f,-.30f,2.23f},{"Saiph",5.7959f,-9.67f,2.06f},{"Mimosa",12.7953f,-59.689f,1.25f},{"Gacrux",12.5194f,-57.113f,1.63f},{"DeltaCrux",12.2524f,-58.749f,2.79f},{"Dubhe",11.0621f,61.751f,1.79f},{"Merak",11.0307f,56.382f,2.37f},{"Phecda",11.8972f,53.694f,2.44f},{"Megrez",12.2571f,57.032f,3.31f},{"Alioth",12.9005f,55.960f,1.77f},{"Mizar",13.3987f,54.925f,2.23f},{"Alkaid",13.7923f,49.313f,1.86f},{"Denebola",11.8177f,14.572f,2.14f},{"Algieba",10.3329f,19.842f,2.08f},{"Zosma",11.2351f,20.524f,2.56f},{"Chertan",11.2373f,15.429f,3.34f},{"Sadr",20.3705f,40.257f,2.23f},{"Albireo",19.512f,27.96f,3.08f},{"Gienah",20.770f,33.97f,2.46f}};
 for(const auto &s:bright){float ra=s.ra*.261799388f,dec=s.dec*.017453293f,cd=std::cos(dec),r=44.f;float size=std::max(2.0f,std::min(5.2f,5.0f-(s.mag+1.46f)*.72f));float cr=s.mag<.5f?1.f:.78f,cg=s.mag<.5f?.88f:.88f,cb=s.mag<.5f?.68f:1.f;if(std::string(s.name)=="Betelgeuse"){cr=1.f;cg=.40f;cb=.24f;}if(std::string(s.name)=="Rigel"||std::string(s.name)=="Vega"){cr=.62f;cg=.78f;cb=1.f;}v.push_back({r*cd*std::cos(ra),r*std::sin(dec),r*cd*std::sin(ra),cr,cg,cb,size});}
 starCount=(GLsizei)v.size();upload(starVbo,v.data(),v.size()*sizeof(StarVertex),GL_ARRAY_BUFFER);
}
void createConstellationLines(){
 struct P{float ra,dec;};auto xyz=[](P p){float ra=p.ra*.261799388f,dec=p.dec*.017453293f,cd=std::cos(dec);return Vec3{44.f*cd*std::cos(ra),44.f*std::sin(dec),44.f*cd*std::sin(ra)};};
 std::vector<float> v;auto seg=[&](P a,P b){Vec3 p=xyz(a),q=xyz(b);v.insert(v.end(),{p.x,p.y,p.z,q.x,q.y,q.z});};
 const P o[]={{5.9195f,7.407f},{5.4189f,6.35f},{5.6793f,-1.94f},{5.6036f,-1.20f},{5.5334f,-.30f},{5.7959f,-9.67f},{5.2423f,-8.202f}};
 seg(o[0],o[1]);seg(o[1],o[2]);seg(o[2],o[3]);seg(o[3],o[4]);seg(o[4],o[1]);seg(o[2],o[5]);seg(o[5],o[6]);seg(o[6],o[2]);
 const P cr[]={{12.4433f,-63.099f},{12.7953f,-59.689f},{12.5194f,-57.113f},{12.2524f,-58.749f}};seg(cr[0],cr[2]);seg(cr[1],cr[3]);
 const P u[]={{11.0621f,61.751f},{11.0307f,56.382f},{11.8972f,53.694f},{12.2571f,57.032f},{12.9005f,55.960f},{13.3987f,54.925f},{13.7923f,49.313f}};for(int i=0;i<6;i++)seg(u[i],u[i+1]);
 const P l[]={{10.1395f,11.967f},{10.3329f,19.842f},{11.2351f,20.524f},{11.8177f,14.572f}};seg(l[0],l[1]);seg(l[1],l[2]);seg(l[2],l[3]);seg(l[3],l[0]);
 const P cy[]={{20.6905f,45.280f},{20.3705f,40.257f},{19.512f,27.96f},{20.770f,33.97f}};seg(cy[0],cy[1]);seg(cy[1],cy[2]);seg(cy[1],cy[3]);
 constellationVertexCount=(GLsizei)(v.size()/3);upload(constellationVbo,v.data(),v.size()*sizeof(float),GL_ARRAY_BUFFER);
}
void createMilkyWay(int count){
 std::vector<float> v;v.reserve((size_t)count*3);unsigned int seed=7312026u;
 auto rnd=[&](){seed=1664525u*seed+1013904223u;return (seed&0x00ffffffu)/16777216.f;};
 for(int i=0;i<count;i++){
  float a=rnd()*6.2831853f;
  float band=(rnd()+rnd()+rnd()-1.5f)*2.0f;
  float radius=43.f+rnd()*5.f;
  float horizontal=std::sqrt(std::max(0.f,1.f-band*band/16.f));
  v.insert(v.end(),{radius*horizontal*std::cos(a),radius*band*.25f,radius*horizontal*std::sin(a)});
 }
 milkyWayCount=count;upload(milkyWayVbo,v.data(),v.size()*sizeof(float),GL_ARRAY_BUFFER);
}
void createOrbits(){const float radii[]={2.2f,3.3f,4.6f,6.2f,8.2f};for(float radius:radii){std::vector<float> v;for(int i=0;i<=360;i++){float a=i*0.0174532925f;v.insert(v.end(),{std::cos(a)*radius,0.f,std::sin(a)*radius});}GLuint b=0;upload(b,v.data(),v.size()*sizeof(float),GL_ARRAY_BUFFER);orbitVbos.push_back(b);}}
void setMatrices(const float *mdl){multiply(mvp,vp,mdl);glUniformMatrix4fv(uMvp,1,GL_FALSE,mvp);glUniformMatrix4fv(uModel,1,GL_FALSE,mdl);}
void drawSphere(float x,float y,float z,float radius,float r,float g,float b,bool lit){
 identity(model);model[0]=model[5]=model[10]=radius;model[12]=x;model[13]=y;model[14]=z;setMatrices(model);
 glUniform4f(uColor,r,g,b,1);glUniform3f(uLight,-5,8,7);glUniform1i(uLighting,lit?1:0);glUniform1i(uUsePointStyle,0);glUniform1f(uPointSize,1);
 glBindBuffer(GL_ARRAY_BUFFER,sphereVbo);glEnableVertexAttribArray(aPosition);glVertexAttribPointer(aPosition,3,GL_FLOAT,GL_FALSE,0,0);
 glBindBuffer(GL_ARRAY_BUFFER,sphereNbo);glEnableVertexAttribArray(aNormal);glVertexAttribPointer(aNormal,3,GL_FLOAT,GL_FALSE,0,0);
 glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,sphereIbo);glDrawElements(GL_TRIANGLES,sphereIndexCount,GL_UNSIGNED_SHORT,0);
 glDisableVertexAttribArray(aPosition);glDisableVertexAttribArray(aNormal);
}
void drawLines(GLuint buffer,GLsizei count,float r,float g,float b){identity(model);setMatrices(model);glUniform4f(uColor,r,g,b,1);glUniform1i(uLighting,0);glUniform1i(uUsePointStyle,0);glUniform1f(uPointSize,1);glBindBuffer(GL_ARRAY_BUFFER,buffer);glEnableVertexAttribArray(aPosition);glVertexAttribPointer(aPosition,3,GL_FLOAT,GL_FALSE,0,0);glDisableVertexAttribArray(aNormal);glVertexAttrib3f(aNormal,0,1,0);glDrawArrays(GL_LINE_STRIP,0,count);glDisableVertexAttribArray(aPosition);}
drawStars(){identity(model);setMatrices(model);glUniform1i(uLighting,0);glUniform1i(uUsePointStyle,1);glBindBuffer(GL_ARRAY_BUFFER,starVbo);glEnableVertexAttribArray(aPosition);glVertexAttribPointer(aPosition,3,GL_FLOAT,GL_FALSE,sizeof(StarVertex),(void*)0);glEnableVertexAttribArray(aPointColor);glVertexAttribPointer(aPointColor,3,GL_FLOAT,GL_FALSE,sizeof(StarVertex),(void*)(3*sizeof(float)));glEnableVertexAttribArray(aStarSize);glVertexAttribPointer(aStarSize,1,GL_FLOAT,GL_FALSE,sizeof(StarVertex),(void*)(6*sizeof(float)));glDrawArrays(GL_POINTS,0,starCount);glDisableVertexAttribArray(aPosition);glDisableVertexAttribArray(aPointColor);glDisableVertexAttribArray(aStarSize);glUniform1i(uUsePointStyle,0);}
void drawConstellations(){if(!constellationVbo||constellationVertexCount<=0)return;identity(model);setMatrices(model);glUniform4f(uColor,.24f,.58f,.95f,.78f);glUniform1i(uLighting,0);glUniform1i(uUsePointStyle,0);glUniform1f(uPointSize,1.f);glBindBuffer(GL_ARRAY_BUFFER,constellationVbo);glEnableVertexAttribArray(aPosition);glVertexAttribPointer(aPosition,3,GL_FLOAT,GL_FALSE,0,0);glDisableVertexAttribArray(aNormal);glVertexAttrib3f(aNormal,0,1,0);glDrawArrays(GL_LINES,0,constellationVertexCount);glDisableVertexAttribArray(aPosition);}
void drawMilkyWay(){identity(model);setMatrices(model);glUniform4f(uColor,.20f,.25f,.48f,.42f);glUniform1i(uLighting,0);glUniform1i(uUsePointStyle,0);glUniform1f(uPointSize,quality>=2?2.0f:1.35f);glBindBuffer(GL_ARRAY_BUFFER,milkyWayVbo);glEnableVertexAttribArray(aPosition);glVertexAttribPointer(aPosition,3,GL_FLOAT,GL_FALSE,0,0);glDisableVertexAttribArray(aNormal);glVertexAttrib3f(aNormal,0,1,0);glDrawArrays(GL_POINTS,0,milkyWayCount);glDisableVertexAttribArray(aPosition);}
void drawEphemerisOrbit(int body){std::vector<float> vertices;vertices.reserve(EPHEMERIS_STEPS*3);for(int i=0;i<EPHEMERIS_STEPS;i++){const Vec3 p=ephemeris[body*EPHEMERIS_STEPS+i];vertices.insert(vertices.end(),{p.x*.62f,p.y*.62f,p.z*.62f});}GLuint b=ephemerisOrbitVbos[body];glBindBuffer(GL_ARRAY_BUFFER,b);glBufferData(GL_ARRAY_BUFFER,vertices.size()*sizeof(float),vertices.data(),GL_DYNAMIC_DRAW);identity(model);setMatrices(model);const float colors[8][3]={{.65f,.58f,.45f},{.75f,.60f,.42f},{.30f,.55f,1.f},{.90f,.35f,.20f},{.85f,.68f,.38f},{.75f,.72f,.60f},{.35f,.75f,.82f},{.30f,.40f,.90f}};glUniform4f(uColor,colors[body][0],colors[body][1],colors[body][2],.8f);glUniform1i(uLighting,0);glUniform1i(uUsePointStyle,0);glUniform1f(uPointSize,1.f);glEnableVertexAttribArray(aPosition);glVertexAttribPointer(aPosition,3,GL_FLOAT,GL_FALSE,0,0);glDrawArrays(GL_LINE_STRIP,0,EPHEMERIS_STEPS);glDisableVertexAttribArray(aPosition);}
void releaseObjects(){GLuint ids[]={sphereVbo,sphereNbo,sphereIbo,starVbo,milkyWayVbo,constellationVbo};glDeleteBuffers(6,ids);constellationVbo=0;constellationVertexCount=0;glDeleteBuffers(PLANET_COUNT,ephemerisOrbitVbos);std::fill(ephemerisOrbitVbos,ephemerisOrbitVbos+PLANET_COUNT,0);for(GLuint b:orbitVbos)glDeleteBuffers(1,&b);orbitVbos.clear();if(program)glDeleteProgram(program);program=0;ephemerisReady=false;}
}

extern "C" JNIEXPORT void JNICALL
Java_br_com_observatorioespacial_NativeBridge_nativeInit(JNIEnv *env,jobject,jint memoryClassMb){
 releaseObjects();const char *renderer=(const char*)glGetString(GL_RENDERER);const char *version=(const char*)glGetString(GL_VERSION);const char *vendor=(const char*)glGetString(GL_VENDOR);
 GLint maxTex=0,maxAttribs=0;glGetIntegerv(GL_MAX_TEXTURE_SIZE,&maxTex);glGetIntegerv(GL_MAX_VERTEX_ATTRIBS,&maxAttribs);
 quality=(memoryClassMb>=256 && maxTex>=4096 && maxAttribs>=8)?2:((memoryClassMb>=128 && maxTex>=2048)?1:0);
 char info[1024];std::snprintf(info,sizeof(info),"GPU: %s | %s | textura máx. %d | atributos %d | perfil %s",renderer?renderer:"?",version?version:"OpenGL ES",maxTex,maxAttribs,quality==2?"alto":quality==1?"médio":"econômico");gpuInfo=info;
 if(!createProgram()){gpuInfo+=" | erro ao criar shaders";return;}
 glGenBuffers(PLANET_COUNT,ephemerisOrbitVbos);
 createSphere(quality==2?56:(quality==1?40:24),quality==2?72:(quality==1?56:32));
 createStars(quality==2?4200:(quality==1?2400:1000));createConstellationLines();createMilkyWay(quality==2?11000:(quality==1?6000:2600));
 glEnable(GL_DEPTH_TEST);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glClearColor(.008f,.012f,.035f,1.f);
}
extern "C" JNIEXPORT void JNICALL
Java_br_com_observatorioespacial_NativeBridge_nativeSetEphemeris(JNIEnv *env,jobject,jfloatArray values){
 if(!values)return;jsize n=env->GetArrayLength(values);if(n!=PLANET_COUNT*EPHEMERIS_STEPS*3)return;
 jfloat *raw=env->GetFloatArrayElements(values,nullptr);if(!raw)return;
 for(int i=0;i<PLANET_COUNT*EPHEMERIS_STEPS;i++)ephemeris[i]={raw[i*3],raw[i*3+1],raw[i*3+2]};
 env->ReleaseFloatArrayElements(values,raw,JNI_ABORT);ephemerisReady=true;
}
extern "C" JNIEXPORT void JNICALL
Java_br_com_observatorioespacial_NativeBridge_nativeResize(JNIEnv*,jobject,jint w,jint h){
 surfaceW=std::max(1,(int)w);surfaceH=std::max(1,(int)h);glViewport(0,0,surfaceW,surfaceH);perspective(projection,48.f,(float)surfaceW/surfaceH,.1f,120.f);
}
extern "C" JNIEXPORT void JNICALL
Java_br_com_observatorioespacial_NativeBridge_nativeRender(JNIEnv*,jobject,jfloat seconds,jfloat zoom,jfloat yaw,jfloat pitch){
 if(!program)return;glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glUseProgram(program);
 float distance=24.f/std::max(.65f,std::min(2.6f,zoom));Vec3 eye={std::sin(yaw)*distance,5.f+pitch*10.f,std::cos(yaw)*distance};
 lookAt(view,eye,{0,0,0},{0,1,0});multiply(vp,projection,view);
 drawMilkyWay();drawStars();drawConstellations();drawSphere(0,0,0,.72f,1.f,.66f,.20f,false);
 if(ephemerisReady){
  const float colors[8][3]={{.65f,.58f,.45f},{.75f,.60f,.42f},{.30f,.55f,1.f},{.90f,.35f,.20f},{.85f,.68f,.38f},{.75f,.72f,.60f},{.35f,.75f,.82f},{.30f,.40f,.90f}};
  const float sizes[8]={.12f,.18f,.20f,.16f,.48f,.40f,.31f,.30f};
  float h=std::fmod(seconds*.2f,(float)(EPHEMERIS_STEPS-1));int i0=(int)h;float f=h-i0;
  for(int i=0;i<PLANET_COUNT;i++){
   drawEphemerisOrbit(i);
   Vec3 a=ephemeris[i*EPHEMERIS_STEPS+i0],b=ephemeris[i*EPHEMERIS_STEPS+std::min(i0+1,EPHEMERIS_STEPS-1)];
   float x=(a.x+(b.x-a.x)*f)*.62f,y=(a.y+(b.y-a.y)*f)*.62f,z=(a.z+(b.z-a.z)*f)*.62f;
   drawSphere(x,y,z,sizes[i],colors[i][0],colors[i][1],colors[i][2],true);
  }
 }else{
  float p[15]={};observatorio_compute_orbits(seconds,p,15);
  const float styles[5][4]={{.92f,.15f,.15f,.17f},{1.f,.38f,.29f,.12f},{1.f,.18f,.43f,.95f},{.88f,.72f,.25f,.12f},{1.25f,.75f,.59f,.34f}};
  for(int i=0;i<5;i++)drawSphere(p[i*3],p[i*3+1],p[i*3+2],styles[i][0],styles[i][1],styles[i][2],styles[i][3],true);
 }
}
extern "C" JNIEXPORT jstring JNICALL
Java_br_com_observatorioespacial_NativeBridge_getGpuRenderer(JNIEnv *env,jobject){return env->NewStringUTF(gpuInfo.c_str());}
extern "C" JNIEXPORT jint JNICALL
Java_br_com_observatorioespacial_NativeBridge_getCpuCoreCount(JNIEnv*,jobject){unsigned int n=std::thread::hardware_concurrency();return (jint)(n?n:1);}

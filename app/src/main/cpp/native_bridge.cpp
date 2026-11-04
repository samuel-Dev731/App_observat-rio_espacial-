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
GLuint program=0, sphereVbo=0, sphereNbo=0, sphereIbo=0, starVbo=0, milkyWayVbo=0;
GLint aPosition=-1,aNormal=-1,uMvp=-1,uModel=-1,uColor=-1,uLight=-1,uLighting=-1,uPointSize=-1;
GLsizei sphereIndexCount=0, starCount=0, milkyWayCount=0;\nconstexpr int PLANET_COUNT=8, EPHEMERIS_STEPS=25;\nstd::vector<Vec3> ephemeris(PLANET_COUNT*EPHEMERIS_STEPS);\nbool ephemerisReady=false;
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
 const char *vs="uniform mat4 uMVP; uniform mat4 uModel; uniform float uPointSize; attribute vec3 aPosition; attribute vec3 aNormal; varying vec3 vNormal; varying vec3 vPosition; void main(){vec4 w=uModel*vec4(aPosition,1.0);vPosition=w.xyz;vNormal=mat3(uModel)*aNormal;gl_Position=uMVP*vec4(aPosition,1.0);gl_PointSize=uPointSize;}";
 const char *fs="precision mediump float; uniform vec4 uColor; uniform vec3 uLight; uniform int uLighting; varying vec3 vNormal; varying vec3 vPosition; void main(){float shade=1.0;if(uLighting==1){vec3 n=normalize(vNormal);vec3 l=normalize(uLight-vPosition);shade=0.20+max(dot(n,l),0.0)*0.90;}gl_FragColor=vec4(uColor.rgb*shade,uColor.a);}";
 GLuint v=shader(GL_VERTEX_SHADER,vs),f=shader(GL_FRAGMENT_SHADER,fs);if(!v||!f)return false;
 program=glCreateProgram();glAttachShader(program,v);glAttachShader(program,f);glLinkProgram(program);glDeleteShader(v);glDeleteShader(f);
 GLint ok=0;glGetProgramiv(program,GL_LINK_STATUS,&ok);if(!ok){glDeleteProgram(program);program=0;return false;}
 aPosition=glGetAttribLocation(program,"aPosition");aNormal=glGetAttribLocation(program,"aNormal");
 uMvp=glGetUniformLocation(program,"uMVP");uModel=glGetUniformLocation(program,"uModel");uColor=glGetUniformLocation(program,"uColor");
 uLight=glGetUniformLocation(program,"uLight");uLighting=glGetUniformLocation(program,"uLighting");uPointSize=glGetUniformLocation(program,"uPointSize");return true;
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
 std::vector<float> v;v.reserve((size_t)count*3);unsigned int seed=2026;
 auto rnd=[&](){seed=1664525u*seed+1013904223u;return (seed&0x00ffffffu)/16777216.f;};
 for(int i=0;i<count;i++){float a=rnd()*6.2831853f,z=rnd()*2.f-1.f,r=38.f+rnd()*12.f,q=std::sqrt(1-z*z);v.insert(v.end(),{r*q*std::cos(a),r*z,r*q*std::sin(a)});}
 starCount=count;upload(starVbo,v.data(),v.size()*sizeof(float),GL_ARRAY_BUFFER);
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
 glUniform4f(uColor,r,g,b,1);glUniform3f(uLight,-5,8,7);glUniform1i(uLighting,lit?1:0);glUniform1f(uPointSize,1);
 glBindBuffer(GL_ARRAY_BUFFER,sphereVbo);glEnableVertexAttribArray(aPosition);glVertexAttribPointer(aPosition,3,GL_FLOAT,GL_FALSE,0,0);
 glBindBuffer(GL_ARRAY_BUFFER,sphereNbo);glEnableVertexAttribArray(aNormal);glVertexAttribPointer(aNormal,3,GL_FLOAT,GL_FALSE,0,0);
 glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,sphereIbo);glDrawElements(GL_TRIANGLES,sphereIndexCount,GL_UNSIGNED_SHORT,0);
 glDisableVertexAttribArray(aPosition);glDisableVertexAttribArray(aNormal);
}
void drawLines(GLuint buffer,GLsizei count,float r,float g,float b){identity(model);setMatrices(model);glUniform4f(uColor,r,g,b,1);glUniform1i(uLighting,0);glUniform1f(uPointSize,1);glBindBuffer(GL_ARRAY_BUFFER,buffer);glEnableVertexAttribArray(aPosition);glVertexAttribPointer(aPosition,3,GL_FLOAT,GL_FALSE,0,0);glDisableVertexAttribArray(aNormal);glVertexAttrib3f(aNormal,0,1,0);glDrawArrays(GL_LINE_STRIP,0,count);glDisableVertexAttribArray(aPosition);}
void drawStars(){identity(model);setMatrices(model);glUniform4f(uColor,.78f,.86f,1,1);glUniform1i(uLighting,0);glUniform1f(uPointSize,quality>=2?1.8f:1.3f);glBindBuffer(GL_ARRAY_BUFFER,starVbo);glEnableVertexAttribArray(aPosition);glVertexAttribPointer(aPosition,3,GL_FLOAT,GL_FALSE,0,0);glDisableVertexAttribArray(aNormal);glVertexAttrib3f(aNormal,0,1,0);glDrawArrays(GL_POINTS,0,starCount);glDisableVertexAttribArray(aPosition);}
void drawMilkyWay(){identity(model);setMatrices(model);glUniform4f(uColor,.20f,.25f,.48f,.42f);glUniform1i(uLighting,0);glUniform1f(uPointSize,quality>=2?2.0f:1.35f);glBindBuffer(GL_ARRAY_BUFFER,milkyWayVbo);glEnableVertexAttribArray(aPosition);glVertexAttribPointer(aPosition,3,GL_FLOAT,GL_FALSE,0,0);glDisableVertexAttribArray(aNormal);glVertexAttrib3f(aNormal,0,1,0);glDrawArrays(GL_POINTS,0,milkyWayCount);glDisableVertexAttribArray(aPosition);}\nvoid drawEphemerisOrbit(int body){std::vector<float> vertices;vertices.reserve(EPHEMERIS_STEPS*3);for(int i=0;i<EPHEMERIS_STEPS;i++){const Vec3 p=ephemeris[body*EPHEMERIS_STEPS+i];vertices.insert(vertices.end(),{p.x*.62f,p.y*.62f,p.z*.62f});}GLuint b=0;glGenBuffers(1,&b);glBindBuffer(GL_ARRAY_BUFFER,b);glBufferData(GL_ARRAY_BUFFER,vertices.size()*sizeof(float),vertices.data(),GL_DYNAMIC_DRAW);identity(model);setMatrices(model);const float colors[8][3]={{.65f,.58f,.45f},{.75f,.60f,.42f},{.30f,.55f,1.f},{.90f,.35f,.20f},{.85f,.68f,.38f},{.75f,.72f,.60f},{.35f,.75f,.82f},{.30f,.40f,.90f}};glUniform4f(uColor,colors[body][0],colors[body][1],colors[body][2],.8f);glUniform1i(uLighting,0);glUniform1f(uPointSize,1.f);glEnableVertexAttribArray(aPosition);glVertexAttribPointer(aPosition,3,GL_FLOAT,GL_FALSE,0,0);glDrawArrays(GL_LINE_STRIP,0,EPHEMERIS_STEPS);glDisableVertexAttribArray(aPosition);glDeleteBuffers(1,&b);}\nvoid releaseObjects(){GLuint ids[]={sphereVbo,sphereNbo,sphereIbo,starVbo,milkyWayVbo};glDeleteBuffers(5,ids);for(GLuint b:orbitVbos)glDeleteBuffers(1,&b);orbitVbos.clear();if(program)glDeleteProgram(program);program=0;ephemerisReady=false;}
}

extern "C" JNIEXPORT void JNICALL
Java_br_com_observatorioespacial_NativeBridge_nativeInit(JNIEnv *env,jobject,jint memoryClassMb){
 releaseObjects();const char *renderer=(const char*)glGetString(GL_RENDERER);const char *version=(const char*)glGetString(GL_VERSION);const char *vendor=(const char*)glGetString(GL_VENDOR);
 GLint maxTex=0,maxAttribs=0;glGetIntegerv(GL_MAX_TEXTURE_SIZE,&maxTex);glGetIntegerv(GL_MAX_VERTEX_ATTRIBS,&maxAttribs);
 quality=(memoryClassMb>=256 && maxTex>=4096 && maxAttribs>=8)?2:((memoryClassMb>=128 && maxTex>=2048)?1:0);
 char info[1024];std::snprintf(info,sizeof(info),"GPU: %s | %s | textura máx. %d | atributos %d | perfil %s",renderer?renderer:"?",version?version:"OpenGL ES",maxTex,maxAttribs,quality==2?"alto":quality==1?"médio":"econômico");gpuInfo=info;
 if(!createProgram()){gpuInfo+=" | erro ao criar shaders";return;}
 createSphere(quality==2?56:(quality==1?40:24),quality==2?72:(quality==1?56:32));
 createStars(quality==2?2600:(quality==1?1500:650));createMilkyWay(quality==2?9000:(quality==1?5000:2200));
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
 drawMilkyWay();drawStars();for(size_t i=0;i<orbitVbos.size();i++)drawLines(orbitVbos[i],361,.20f,.25f,.38f);
 drawSphere(0,0,0,.72f,1.f,.66f,.20f,false);
 float p[15]={};observatorio_compute_orbits(seconds,p,15);
 const float styles[5][4]={{.92f,.15f,.15f,.17f},{1.f,.38f,.29f,.12f},{1.f,.18f,.43f,.95f},{.88f,.72f,.25f,.12f},{1.25f,.75f,.59f,.34f}};
 for(int i=0;i<5;i++)drawSphere(p[i*3],p[i*3+1],p[i*3+2],styles[i][0],styles[i][1],styles[i][2],styles[i][3],true);
}
extern "C" JNIEXPORT jstring JNICALL
Java_br_com_observatorioespacial_NativeBridge_getGpuRenderer(JNIEnv *env,jobject){return env->NewStringUTF(gpuInfo.c_str());}
extern "C" JNIEXPORT jint JNICALL
Java_br_com_observatorioespacial_NativeBridge_getCpuCoreCount(JNIEnv*,jobject){unsigned int n=std::thread::hardware_concurrency();return (jint)(n?n:1);}
